package ru.lukin.edododo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import ru.lukin.edododo.config.SabyAppProperties;
import ru.lukin.edododo.dto.StatusUpdateRequest;
import ru.lukin.edododo.exception.BadRequestException;
import ru.lukin.edododo.exception.ResourceNotFoundException;
import ru.lukin.edododo.model.ActDocument;
import ru.lukin.edododo.model.ActStatus;
import ru.lukin.edododo.model.FileDocument;
import ru.lukin.edododo.model.HistoryEntry;
import ru.lukin.edododo.repository.ActRepository;
import ru.lukin.edododo.repository.FileRepository;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalField;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
public class ActServiceImpl implements ActService {

    private static final List<String> VALID_STATUSES = Arrays.stream(ActStatus.values())
            .map(ActStatus::getDisplayName)
            .toList();

    private final ActRepository actRepository;
    private final FileRepository fileRepository;
    private final SabyService sabyService;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final CounterpartyExceptionService counterpartyExceptionService;
    private final SabyAppProperties sabyAppProperties;
    @Value("${app.uploads-dir:uploads}")
    private String uploadsDir;
    public ActServiceImpl(
            ActRepository actRepository,
            FileRepository fileRepository,
            SabyService sabyService,
            MongoTemplate mongoTemplate,
            ObjectMapper objectMapper,
            CounterpartyExceptionService counterpartyExceptionService,
            SabyAppProperties sabyAppProperties
    ) {
        this.actRepository = actRepository;
        this.fileRepository = fileRepository;
        this.sabyService = sabyService;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
        this.counterpartyExceptionService = counterpartyExceptionService;
        this.sabyAppProperties = sabyAppProperties;
    }

    private void enrichExceptionFlag(ActDocument act) {
        act.setCounterpartyException(counterpartyExceptionService.isException(act));
    }

    private void enrichExceptionFlags(List<ActDocument> acts) {
        acts.forEach(this::enrichExceptionFlag);
    }

    private Criteria buildCriteria(String period, String legalEntity, String search) {
        List<Criteria> criteria = new ArrayList<>();
        if (period != null && !period.isBlank()) criteria.add(Criteria.where("period").is(period));
        if (legalEntity != null && !legalEntity.isBlank()) criteria.add(Criteria.where("legalEntity").is(legalEntity));
        if (search != null && !search.isBlank()) {
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("actNumber").regex(search, "i"),
                    Criteria.where("counterparty").regex(search, "i"),
                    Criteria.where("inn").regex(search, "i"),
                    Criteria.where("legalEntity").regex(search, "i"),
                    Criteria.where("responsibleAccountant").regex(search, "i")
            );
            criteria.add(searchCriteria);
        }
        return criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria.toArray(new Criteria[0]));
    }

    @Override
    public Map<String, Object> getDashboardStats(String period, String legalEntity, String search) {
        Criteria criteria = buildCriteria(period, legalEntity, search);
        List<AggregationOperation> ops = new ArrayList<>();
        if (criteria.getCriteriaObject() != null && !criteria.getCriteriaObject().isEmpty()) {
            ops.add(match(criteria));
        }
        ops.add(group("status").count().as("count"));

        AggregationResults<Map> results = mongoTemplate.aggregate(newAggregation(ops), "acts", Map.class);
        Map<String, Long> statusCounts = new HashMap<>();
        for (Map item : results.getMappedResults()) {
            statusCounts.put(String.valueOf(item.get("_id")), ((Number) item.get("count")).longValue());
        }

        long total = statusCounts.values().stream().mapToLong(Long::longValue).sum();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_acts", total);
        stats.put("ready_to_send", statusCounts.getOrDefault("Загружено", 0L));
        stats.put("sent_waiting", statusCounts.getOrDefault("Отправлено в СБИС", 0L)
                + statusCounts.getOrDefault("Отправлено Контрагенту", 0L)
                + statusCounts.getOrDefault("Нет ответа", 0L));
        stats.put("signed", statusCounts.getOrDefault("Получен подписанный", 0L));
        stats.put("overdue", statusCounts.getOrDefault("Нет ответа", 0L));
        stats.put("corrections", statusCounts.getOrDefault("Корректировки", 0L));
        stats.put("escalated", statusCounts.getOrDefault("В работе бухгалтерии", 0L));
        stats.put("closed", statusCounts.getOrDefault("Закрыт", 0L));
        stats.put("status_breakdown", statusCounts);
        return stats;
    }

    @Override
    public Map<String, Object> getAttentionItems(String period, String legalEntity, String search) {  // ← Map!
        Criteria base = buildCriteria(period, legalEntity, search);

        // 1. Acts with no response
        Criteria noRespCriteria = new Criteria().andOperator(base, Criteria.where("status").is("Нет ответа"));
        long noResponseCount = mongoTemplate.count(org.springframework.data.mongodb.core.query.Query.query(noRespCriteria), "acts");

        // 2. Large tail counterparties
        Criteria largeTailCriteria = new Criteria().andOperator(base, Criteria.where("status").nin("Закрыт", "Получен подписанный"));
        Aggregation largeTailAgg = newAggregation(
                match(largeTailCriteria),
                group("counterparty").sum("amount").as("total"),
                match(Criteria.where("total").gte(2000000)),
                count().as("count")
        );
        AggregationResults<Map> largeTailResults = mongoTemplate.aggregate(largeTailAgg, "acts", Map.class);
        long largeTailCount = largeTailResults.getMappedResults().isEmpty()
                ? 0L
                : ((Number) largeTailResults.getMappedResults().get(0).get("count")).longValue();

        // 3. Low closure rate
        List<AggregationOperation> lowClosureOps = new ArrayList<>();
        if (!base.getCriteriaObject().isEmpty()) {
            lowClosureOps.add(match(base));
        }
        lowClosureOps.add(group("legalEntity")
                .count().as("total")
                .sum(ConditionalOperators.when(Criteria.where("status").is("Закрыт"))
                        .then(1)
                        .otherwise(0)).as("closed"));
        lowClosureOps.add(project("total", "closed")
                .and(ConditionalOperators.when(Criteria.where("total").gt(0))
                        .thenValueOf(ArithmeticOperators.Divide.valueOf("closed").divideBy("total"))
                        .otherwise(0)).as("rate"));
        lowClosureOps.add(match(Criteria.where("rate").lt(0.4)));
        lowClosureOps.add(count().as("count"));

        AggregationResults<Map> lowClosureResults = mongoTemplate.aggregate(
                newAggregation(lowClosureOps), "acts", Map.class
        );
        long lowClosureCount = lowClosureResults.getMappedResults().isEmpty()
                ? 0L
                : ((Number) lowClosureResults.getMappedResults().get(0).get("count")).longValue();

        // ← ВОТ ЗДЕСЬ ИЗМЕНЕНИЕ: return Map, НЕ List!
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("no_response_acts", noResponseCount);
        result.put("large_tail_counterparties", largeTailCount);
        result.put("low_closure_entities", lowClosureCount);
        return result;  // ← Object!
    }

    @Override
    public List<Map<String, Object>> getProcessStages(String period, String legalEntity, String search) {
        Map<String, Object> stats = getDashboardStats(period, legalEntity, search);
        Map<String, Long> breakdown = (Map<String, Long>) stats.get("status_breakdown");

        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(Map.of("name", "Загружено", "count", breakdown.getOrDefault("Загружено", 0L)));
        stages.add(Map.of("name", "Отправлено в СБИС", "count", breakdown.getOrDefault("Отправлено в СБИС", 0L)));
        stages.add(Map.of("name", "Отправлено Контрагенту", "count", breakdown.getOrDefault("Отправлено Контрагенту", 0L)));
        stages.add(Map.of("name", "Ждём ответ", "count", breakdown.getOrDefault("Нет ответа", 0L)
                + breakdown.getOrDefault("Отправлено в СБИС", 0L) + breakdown.getOrDefault("Отправлено Контрагенту", 0L)));
        stages.add(Map.of("name", "Получен подписанный", "count", breakdown.getOrDefault("Получен подписанный", 0L)));
        stages.add(Map.of("name", "Закрыто", "count", breakdown.getOrDefault("Закрыт", 0L)));
        return stages;
    }

    @Override
    public Map<String, Object> getActs(String status, String legalEntity, String counterparty, String period, String search, int page, int limit) {
        List<Criteria> criteria = new ArrayList<>();
        if (status != null && !status.isBlank()) criteria.add(Criteria.where("status").is(status));
        if (legalEntity != null && !legalEntity.isBlank()) criteria.add(Criteria.where("legalEntity").is(legalEntity));
        if (counterparty != null && !counterparty.isBlank())
            criteria.add(Criteria.where("counterparty").is(counterparty));
        if (period != null && !period.isBlank()) criteria.add(Criteria.where("period").is(period));
        if (search != null && !search.isBlank()) {
            criteria.add(new Criteria().orOperator(
                    Criteria.where("actNumber").regex(search, "i"),
                    Criteria.where("counterparty").regex(search, "i"),
                    Criteria.where("inn").regex(search, "i")
            ));
        }

        Criteria finalCriteria = criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria.toArray(new Criteria[0]));

        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query(finalCriteria);
        long total = mongoTemplate.count(query, ActDocument.class);

        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        query.skip((long) (page - 1) * limit);
        query.limit(limit);

        List<ActDocument> acts = mongoTemplate.find(query, ActDocument.class);
        enrichExceptionFlags(acts);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("acts", acts);
        response.put("total", total);
        response.put("page", page);
        response.put("limit", limit);
        return response;
    }

    @Override
    public Map<String, Object> exportActs(String status) {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        if (status != null && !status.isBlank()) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        List<ActDocument> acts = mongoTemplate.find(query, ActDocument.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("acts", acts);
        result.put("exported_at", Instant.now().toString());
        result.put("count", acts.size());
        return result;
    }

    @Override
    public ActDocument getActById(String actId) {
        ActDocument act = actRepository.findById(actId)
                .orElseThrow(() -> new ResourceNotFoundException("Акт не найден"));
        enrichExceptionFlag(act);
        return act;
    }

    @Override
    public ActDocument updateActStatus(String actId, StatusUpdateRequest request) {
        if (!VALID_STATUSES.contains(request.getStatus())) {
            throw new BadRequestException("Недопустимый статус. Допустимые: " + VALID_STATUSES);
        }

        ActDocument act = actRepository.findById(actId)
                .orElseThrow(() -> new ResourceNotFoundException("Акт не найден"));
        if (counterpartyExceptionService.isException(act)) {
            String closed = ActStatus.ЗАКРЫТ.getDisplayName();
            if (!closed.equals(request.getStatus())) {
                throw new BadRequestException(
                        "Для контрагента в исключениях доступен только переход в статус «Закрыт»");
            }
        }
        var prevStatus = act.getStatus();
        act.setStatus(request.getStatus());
        act.setUpdatedAt(Instant.now());
        act.getHistory().add(new HistoryEntry(
                Instant.now(),
                prevStatus,
                request.getStatus(),
                request.getComment() == null ? "" : request.getComment()
        ));
        ActDocument saved = actRepository.save(act);
        enrichExceptionFlag(saved);
        return saved;
    }

    @Override
    public Map<String, Object> sendToSaby(String actId, String documentType, Integer counterpartyResponseWaitDays, String sabyAccountId) {
        ActDocument act = actRepository.findById(actId)
                .orElseThrow(() -> new ResourceNotFoundException("Акт не найден"));
        if (counterpartyExceptionService.isException(act)) {
            throw new BadRequestException("Контрагент в списке исключений — отправка в СБИС недоступна");
        }
        if (!ActStatus.ЗАГРУЖЕНО.getDisplayName().equals(act.getStatus())) {
            throw new BadRequestException("Акт должен быть в статусе '" + ActStatus.ЗАГРУЖЕНО.getDisplayName() + "'");
        }

        int waitDays = resolveCounterpartyResponseWaitDays(counterpartyResponseWaitDays);

        Map<String, Object> sabyResponse = sabyService.sendActToSaby(act, sabyAccountId);
        if (!Boolean.TRUE.equals(sabyResponse.get("success"))) {
            throw new BadRequestException(String.valueOf(sabyResponse.get("message")));
        }

        applySuccessfulSabySend(act, sabyResponse, act.getStatus(), waitDays, sabyAccountId);

        ActDocument saved = actRepository.save(act);
        enrichExceptionFlag(saved);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("act", saved);
        result.put("saby_response", sabyResponse);
        return result;
    }

    @Override
    public Map<String, Object> sendBatchToSaby(Integer counterpartyResponseWaitDays, String sabyAccountId) {
        int waitDays = resolveCounterpartyResponseWaitDays(counterpartyResponseWaitDays);
        List<ActDocument> loadedActs = actRepository.findAll().stream()
                .filter(a -> ActStatus.ЗАГРУЖЕНО.getDisplayName().equals(a.getStatus()))
                .toList();
        int skippedExceptions = (int) loadedActs.stream()
                .filter(counterpartyExceptionService::isException)
                .count();
        List<ActDocument> acts = loadedActs.stream()
                .filter(a -> !counterpartyExceptionService.isException(a))
                .toList();

        int sentCount = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        for (ActDocument act : acts) {
            Map<String, Object> sabyResponse = sabyService.sendActToSaby(act, sabyAccountId);
            if (!Boolean.TRUE.equals(sabyResponse.get("success"))) {
                errors.add(Map.of("act_id", act.getId(), "error", sabyResponse.get("message")));
                continue;
            }

            applySuccessfulSabySend(act, sabyResponse, ActStatus.ЗАГРУЖЕНО.getDisplayName(), waitDays, sabyAccountId);
            actRepository.save(act);
            sentCount++;
        }

        String message = "Отправлено " + sentCount + " актов в СБИС"
                + (errors.isEmpty() ? "" : " (ошибок: " + errors.size() + ")")
                + (skippedExceptions == 0 ? "" : " (пропущено исключений: " + skippedExceptions + ")");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent_count", sentCount);
        result.put("skipped_exceptions", skippedExceptions);
        result.put("errors", errors);
        result.put("message", message);
        return result;
    }

    private int resolveCounterpartyResponseWaitDays(Integer customDays) {
        int days = customDays != null ? customDays : sabyAppProperties.getCounterpartyResponseWaitDays();
        if (days < 1 || days > 365) {
            throw new BadRequestException("Срок ожидания ответа должен быть от 1 до 365 дней");
        }
        return days;
    }

    private void applySuccessfulSabySend(
            ActDocument act,
            Map<String, Object> sabyResponse,
            String previousStatus,
            int waitDays,
            String sabyAccountId
    ) {
        String sabySendId = String.valueOf(sabyResponse.get("document_id"));
        Instant now = Instant.now();
        Instant deadline = now.plus(waitDays, ChronoUnit.DAYS);

        act.setStatus(ActStatus.ОТПРАВЛЕНО_КОНТРАГЕНТУ.getDisplayName());
        act.setSabySendId(sabySendId);
        act.setSabyAccountId(sabyAccountId != null ? sabyAccountId : String.valueOf(sabyResponse.get("account_id")));
        act.setSabyResponse(toJsonString(sabyResponse));
        act.setSentToCounterpartyAt(now);
        act.setCounterpartyResponseWaitDays(waitDays);
        act.setCounterpartyResponseDeadline(deadline);
        act.setUpdatedAt(now);
        act.getHistory().add(new HistoryEntry(
                now,
                previousStatus,
                ActStatus.ОТПРАВЛЕНО_КОНТРАГЕНТУ.getDisplayName(),
                "Подписано и отправлено контрагенту (" + sabyResponse.get("mode") + "). ID: " + sabySendId
                        + ". Ожидание ответа " + waitDays + " дн. до " + deadline
        ));
    }

    private String toJsonString(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    @Override
    public Map<String, Object> uploadActs(MultipartFile file) {
        try {
            Path uploadPath = Paths.get(uploadsDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String savedFileName = null;
            String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
            List<Map<String, String>> rows = new ArrayList<>();

            // Сохраняем исходный файл, если это PDF или XML
            if (filename.endsWith(".pdf") || filename.endsWith(".xml")) {
                savedFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
                Path targetPath = uploadPath.resolve(savedFileName);
                Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            if (filename.endsWith(".json")) {
                String text = new String(file.getBytes(), StandardCharsets.UTF_8);
                ObjectMapper mapper = new ObjectMapper();
                Object parsed = mapper.readValue(text, Object.class);
                if (parsed instanceof List) {
                    for (Object item : (List<?>) parsed) rows.add(mapper.convertValue(item, Map.class));
                } else {
                    rows.add(mapper.convertValue(parsed, Map.class));
                }
            } else if (filename.endsWith(".csv")) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                    String headerLine = br.readLine();
                    if (headerLine == null) throw new BadRequestException("CSV пуст");
                    String[] headers = headerLine.split(",");
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] values = line.split(",", -1);
                        Map<String, String> row = new HashMap<>();
                        for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                            row.put(headers[i].trim(), values[i].trim());
                        }
                        rows.add(row);
                    }
                }
            } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                    Sheet sheet = workbook.getSheetAt(0);
                    Row headerRow = sheet.getRow(0);
                    if (headerRow == null) throw new BadRequestException("Excel пуст");
                    String[] headers = new String[(int) headerRow.getLastCellNum()];
                    for (int i = 0; i < headers.length; i++) {
                        Cell cell = headerRow.getCell(i);
                        headers[i] = cell != null ? cell.toString().trim() : "";
                    }
                    for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                        Row row = sheet.getRow(rowNum);
                        if (row == null) continue;
                        Map<String, String> dataRow = new HashMap<>();
                        for (int i = 0; i < headers.length && i < row.getLastCellNum(); i++) {
                            Cell cell = row.getCell(i);
                            dataRow.put(headers[i], cellToString(cell));
                        }
                        rows.add(dataRow);
                    }
                }
            } else if (filename.endsWith(".xml")) {
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(false);
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document xmlDoc = builder.parse(new ByteArrayInputStream(file.getBytes()));
                    xmlDoc.getDocumentElement().normalize();

                    NodeList documentNodes = xmlDoc.getElementsByTagName("Документ");
                    if (documentNodes.getLength() == 0) {
                        throw new BadRequestException("XML не содержит узел Документ");
                    }

                    for (int i = 0; i < documentNodes.getLength(); i++) {
                        Element documentEl = (Element) documentNodes.item(i);
                        Map<String, String> row = new HashMap<>();

                        // === Основные поля документа ===
                        row.put("act_number", documentEl.getAttribute("НомерАкт"));
                        row.put("formation_date", documentEl.getAttribute("ДатаИнфОтпр"));

                        String startPeriod = documentEl.getAttribute("ДатаНачПер");
                        String endPeriod = documentEl.getAttribute("ДатаОкПер");
                        row.put("period_start", startPeriod);
                        row.put("period_end", endPeriod);
                        row.put("period", buildPeriodFromXml(startPeriod, endPeriod));

                        // === НАША ОРГАНИЗАЦИЯ: ищем вложенные элементы последовательно ===
                        // <Документ> → <СвДокКрАкт> → <СвОтпр> → <ИдСв> → <СвЮЛУч>
                        Element docInfo = getFirstChildElementByTag(documentEl, "СвДокКрАкт");
                        if (docInfo != null) {
                            Element senderBlock = getFirstChildElementByTag(docInfo, "СвОтпр");
                            if (senderBlock != null) {
                                Element idBlock = getFirstChildElementByTag(senderBlock, "ИдСв");
                                if (idBlock != null) {
                                    Element ourCompany = getFirstChildElementByTag(idBlock, "СвЮЛУч");
                                    if (ourCompany != null) {
                                        row.put("our_company_name", ourCompany.getAttribute("НаимОрг"));
                                        row.put("our_company_inn", ourCompany.getAttribute("ИННЮЛ"));
                                        row.put("our_company_kpp", ourCompany.getAttribute("КПП"));
                                    }
                                }
                            }
                        }

                        // === КОНТРАГЕНТ: <СвДокКрАкт> → <СвПол> → <ИдСв> → [СвФЛ | СвИП | СвЮЛ] ===
                        if (docInfo != null) {
                            Element recipientBlock = getFirstChildElementByTag(docInfo, "СвПол");
                            if (recipientBlock != null) {
                                Element idBlock = getFirstChildElementByTag(recipientBlock, "ИдСв");
                                if (idBlock != null) {
                                    // 1. Физическое лицо (СвФЛ)
                                    Element counterpartyFl = getFirstChildElementByTag(idBlock, "СвФЛ");
                                    if (counterpartyFl != null) {
                                        Element fio = getFirstChildElementByTag(counterpartyFl, "ФИО");
                                        if (fio != null) {
                                            String last = fio.getAttribute("Фамилия");
                                            String first = fio.getAttribute("Имя");
                                            String mid = fio.getAttribute("Отчество");
                                            row.put("counterparty", String.join(" ", last, first, mid).trim());
                                            row.put("counterparty_last", last);
                                            row.put("counterparty_first", first);
                                            row.put("counterparty_patronymic", mid);
                                        }
                                        row.put("counterparty_inn", counterpartyFl.getAttribute("ИННФЛ"));
                                        row.put("counterparty_kpp", "");
                                        row.put("counterparty_type", "FL");
                                    }
                                    // 2. Индивидуальный предприниматель (СвИП)
                                    else {
                                        Element counterpartyIp = getFirstChildElementByTag(idBlock, "СвИП");
                                        if (counterpartyIp != null) {
                                            Element fio = getFirstChildElementByTag(counterpartyIp, "ФИО");
                                            if (fio != null) {
                                                String last = fio.getAttribute("Фамилия");
                                                String first = fio.getAttribute("Имя");
                                                String mid = fio.getAttribute("Отчество");
                                                row.put("counterparty", String.join(" ", last, first, mid).trim());
                                                row.put("counterparty_last", last);
                                                row.put("counterparty_first", first);
                                                row.put("counterparty_patronymic", mid);
                                            }
                                            row.put("counterparty_inn", counterpartyIp.getAttribute("ИННФЛ"));
                                            row.put("counterparty_kpp", "");
                                            row.put("counterparty_type", "IP");
                                        }
                                        // 3. Юридическое лицо (СвЮЛ)
                                        else {
                                            Element counterpartyUl = getFirstChildElementByTag(idBlock, "СвЮЛ");
                                            if (counterpartyUl != null) {
                                                row.put("counterparty", counterpartyUl.getAttribute("НаимОрг"));
                                                row.put("counterparty_inn", counterpartyUl.getAttribute("ИННЮЛ"));
                                                row.put("counterparty_kpp", counterpartyUl.getAttribute("КПП"));
                                                row.put("counterparty_type", "UL");
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // === Сумма из <ТаблАкт> ===
                        Element totals = getFirstChildElementByTag(documentEl, "ТаблАкт");
                        if (totals != null) {
                            row.put("amount", firstNonBlank(
                                    totals.getAttribute("ОборотКр"),
                                    totals.getAttribute("ОборотДеб")
                            ));
                        }

                        // Метаданные
                        row.put("source_format", "1c_xml");
                        row.put("source_external_id", xmlDoc.getDocumentElement().getAttribute("ИдФайл"));

                        rows.add(row);
                    }
                } catch (BadRequestException e) {
                    throw e;
                } catch (Exception e) {
                    throw new BadRequestException("Ошибка чтения XML: " + e.getMessage());
                }
            }

            // === Создание ActDocument из распаршенных данных ===
            List<ActDocument> created = new ArrayList<>();
            for (Map<String, String> row : rows) {
                ActDocument act = new ActDocument();
                act.setId(UUID.randomUUID().toString());
                act.setActNumber(firstNonBlank(row, "act_number", "номер_акта"));
                act.setLegalEntity(firstNonBlank(row, "legal_entity", "юрлицо"));

                // Контрагент (новые поля)
                act.setCounterparty(firstNonBlank(row, "counterparty"));
                act.setCounterpartyInn(firstNonBlank(row, "counterparty_inn"));
                act.setCounterpartyKpp(firstNonBlank(row, "counterparty_kpp"));
                act.setCounterpartyType(firstNonBlank(row, "counterparty_type"));
                act.setCounterpartyLastName(firstNonBlank(row, "counterparty_last"));
                act.setCounterpartyFirstName(firstNonBlank(row, "counterparty_first"));
                act.setCounterpartyPatronymic(firstNonBlank(row, "counterparty_patronymic"));

                // Наша организация (новые поля)
                act.setOurCompanyName(firstNonBlank(row, "our_company_name"));
                act.setOurCompanyInn(firstNonBlank(row, "our_company_inn"));
                act.setOurCompanyKpp(firstNonBlank(row, "our_company_kpp"));

                act.setLegalEntity(firstNonBlank(row, "our_company_name"));
                act.setInn(firstNonBlank(row, "our_company_inn"));
                act.setKpp(firstNonBlank(row, "our_company_kpp"));


                act.setPeriod(firstNonBlank(row, "period", "период"));
                act.setFormationDate(firstNonBlank(row, "formation_date", "дата_формирования"));
                act.setAmount(parseDouble(firstNonBlank(row, "amount", "сумма")));
                act.setFilePath(firstNonBlank(row, "file_path", "путь_к_файлу"));
                act.setResponsibleAccountant(firstNonBlank(row, "responsible_accountant", "бухгалтер"));
                act.setSabyRequisites(firstNonBlank(row, "saby_requisites", "реквизиты_сбис"));

                // Путь к файлу
                if (savedFileName != null) {
                    act.setFilePath(uploadPath.resolve(savedFileName).toString());
                } else {
                    String pathFromData = firstNonBlank(row, "file_path", "путь_к_файлу");
                    if (pathFromData != null && !pathFromData.isBlank() && Files.exists(Paths.get(pathFromData))) {
                        act.setFilePath(pathFromData);
                    } else {
                        act.setFilePath(null);
                    }
                }

                act.setStatus(ActStatus.ЗАГРУЖЕНО.getDisplayName());
                act.setCreatedAt(Instant.now());
                act.setUpdatedAt(Instant.now());
                act.setHistory(List.of(new HistoryEntry(Instant.now(), "", ActStatus.ЗАГРУЖЕНО.getDisplayName(), "Загружено из файла")));
                created.add(act);
            }

            // === Сохранение актов и создание вложений (FileDocument) ===
            Path baseDir = Paths.get(uploadsDir).toAbsolutePath().normalize();
            List<ActDocument> savedActs = actRepository.saveAll(created);
            int attachmentsCreated = 0;

            for (ActDocument act : savedActs) {
                Path actDir = baseDir.resolve("acts").resolve(act.getId());
                try {
                    Files.createDirectories(actDir);
                } catch (IOException e) {
                    System.err.println("Не удалось создать папку для акта " + act.getId() + ": " + e.getMessage());
                    continue;
                }

                String originalName = file.getOriginalFilename();
                String safeName = UUID.randomUUID() + "_" + (originalName != null ? originalName : "act");
                Path filePath = actDir.resolve(safeName);

                try {
                    if (Files.notExists(filePath) && (filename.endsWith(".pdf") || filename.endsWith(".xml"))) {
                        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    System.err.println("Не удалось сохранить файл для акта " + act.getId() + ": " + e.getMessage());
                    continue;
                }

                act.setFilePath(filePath.toString());

                FileDocument fileDoc = new FileDocument();
                fileDoc.setId(UUID.randomUUID().toString());
                fileDoc.setActId(act.getId());
                fileDoc.setStoragePath("acts/" + act.getId() + "/" + safeName);
                fileDoc.setOriginalFilename(originalName);
                fileDoc.setContentType(Files.probeContentType(filePath));
                fileDoc.setSize(Files.size(filePath));
                fileDoc.setDeleted(false);
                fileDoc.setCreatedAt(Instant.now());

                fileRepository.save(fileDoc);
                attachmentsCreated++;
            }

            actRepository.saveAll(savedActs);

            return Map.of(
                    "uploaded", savedActs.size(),
                    "attachments_created", attachmentsCreated,
                    "message", "Загружено " + savedActs.size() + " актов, создано " + attachmentsCreated + " вложений"
            );

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Ошибка чтения файла: " + e.getMessage());
        }
    }

    // ✅ ВСПОМОГАТЕЛЬНЫЙ МЕТОД

    private Element findElementInContext(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && tagName.equals(element.getNodeName())) {
                return element;
            }
        }
        return null;
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    @Override
    public byte[] generateSampleFilesZip() {
        Map<String, Object> sampleAct = new HashMap<>();
        sampleAct.put("act_number", "АКТ-ТЕСТ-001");
        sampleAct.put("legal_entity", "ООО \"Альфа Трейд\"");
        sampleAct.put("counterparty", "ООО \"СтройМонтаж\"");
        sampleAct.put("inn", "7701234567");
        sampleAct.put("kpp", "770101001");
        sampleAct.put("period", "2 квартал 2026");
        sampleAct.put("formation_date", "2026-02-01");
        sampleAct.put("amount", 1500000.50);
        sampleAct.put("file_path", "/docs/test_act.pdf");
        sampleAct.put("responsible_accountant", "Петрова Е.И.");
        sampleAct.put("saby_requisites", "saby_org_1234");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {

            // 1. JSON
            String jsonContent = objectMapper.writeValueAsString(List.of(sampleAct));
            zos.putNextEntry(new ZipEntry("sample_act.json"));
            zos.write(jsonContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. CSV
            String[] headers = sampleAct.keySet().toArray(new String[0]);
            String[] row = sampleAct.values().stream().map(Object::toString).toArray(String[]::new);

            StringWriter csvWriter = new StringWriter();
            try (CSVWriter csv = new CSVWriter(csvWriter)) {
                csv.writeNext(headers);
                csv.writeNext(row);
            }
            zos.putNextEntry(new ZipEntry("sample_act.csv"));
            zos.write(csvWriter.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 3. Excel
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("Акты сверки");
                Row headerRow = sheet.createRow(0);
                int col = 0;
                for (String key : sampleAct.keySet()) {
                    headerRow.createCell(col++).setCellValue(key);
                }
                Row dataRow = sheet.createRow(1);
                col = 0;
                for (Object value : sampleAct.values()) {
                    dataRow.createCell(col++).setCellValue(value.toString());
                }

                ByteArrayOutputStream excelOut = new ByteArrayOutputStream();
                wb.write(excelOut);
                zos.putNextEntry(new ZipEntry("sample_act.xlsx"));
                zos.write(excelOut.toByteArray());
                zos.closeEntry();
            }

            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка генерации ZIP шаблонов", e);
        }
    }

    @Override
    public Map<String, Object> seedTestData() {
        actRepository.deleteAll();

        List<String> legalEntities = List.of("ООО «Альфа Трейд»", "ООО «Бета Сервис»", "АО «Гамма Групп»");
        List<String[]> counterparties = List.of(
                new String[]{"ООО «СтройМонтаж»", "7701234567", "770101001"},
                new String[]{"АО «ТехноПром»", "7702345678", "770201001"},
                new String[]{"ООО «ЛогистикПлюс»", "7703456789", "770301001"},
                new String[]{"ИП Иванов А.А.", "771234567890", ""},
                new String[]{"ООО «ЭнергоСнаб»", "7704567890", "770401001"}
        );
        List<String> accountants = List.of("Петрова Е.И.", "Сидорова А.К.", "Козлова М.В.");
        List<String> periods = List.of("1 квартал 2026", "2 квартал 2026");

        // ✅ РЕАЛИСТИЧНАЯ ЦЕПочка статусов
        String uploaded = ActStatus.ЗАГРУЖЕНО.getDisplayName();
        String sentSbis = ActStatus.ОТПРАВЛЕНО_В_СБИС.getDisplayName();
        String sentCounterparty = ActStatus.ОТПРАВЛЕНО_КОНТРАГЕНТУ.getDisplayName();
        String signedReceived = ActStatus.ПОЛУЧЕН_ПОДПИСАННЫЙ.getDisplayName();
        String noResponse = ActStatus.НЕТ_ОТВЕТА.getDisplayName();
        String corrections = ActStatus.КОРРЕКТИРОВКИ.getDisplayName();
        String accounting = ActStatus.В_РАБОТЕ_БУХГАЛТЕРИИ.getDisplayName();
        String closed = ActStatus.ЗАКРЫТ.getDisplayName();

        Map<String, List<String>> statusHistory = Map.of(
                uploaded, List.of(""),
                sentSbis, List.of("", uploaded, sentSbis),
                sentCounterparty, List.of("", uploaded, sentSbis, sentCounterparty),
                noResponse, List.of("", uploaded, sentSbis, noResponse),
                signedReceived, List.of("", uploaded, sentSbis, sentCounterparty, signedReceived),
                corrections, List.of("", uploaded, sentSbis, corrections),
                accounting, List.of("", uploaded, sentSbis, accounting),
                closed, List.of("", uploaded, sentSbis, signedReceived, closed)
        );

        List<Map.Entry<String, Integer>> distribution = List.of(
                Map.entry(uploaded, 25),
                Map.entry(sentSbis, 12),
                Map.entry(sentCounterparty, 5),
                Map.entry(signedReceived, 18),
                Map.entry(noResponse, 10),
                Map.entry(corrections, 8),
                Map.entry(accounting, 12),
                Map.entry(closed, 30)
        );

        List<ActDocument> acts = new ArrayList<>();
        int actNum = 1;
        Random random = new Random();

        for (Map.Entry<String, Integer> item : distribution) {
            String targetStatus = item.getKey();
            for (int i = 0; i < item.getValue(); i++) {
                String[] cp = counterparties.get(random.nextInt(counterparties.size()));
                String le = legalEntities.get(random.nextInt(legalEntities.size()));
                String period = periods.get(random.nextInt(periods.size()));
                String accountant = accountants.get(random.nextInt(accountants.size()));
                double amount = 50000 + random.nextDouble() * (15000000 - 50000);

                ActDocument act = new ActDocument();
                act.setId(UUID.randomUUID().toString());
                act.setActNumber(String.format("АКТ-%04d", actNum++));
                act.setLegalEntity(le);
                act.setCounterparty(cp[0]);
                act.setInn(cp[1]);
                act.setKpp(cp[2]);
                act.setPeriod(period);
                act.setFormationDate(Instant.now().minusSeconds(random.nextInt(60) * 86400L).toString());
                act.setAmount(Math.round(amount * 100.0) / 100.0);
                act.setFilePath("/exports/acts/" + act.getActNumber() + ".pdf");
                act.setResponsibleAccountant(accountant);
                act.setSabyRequisites("saby_org_" + (1000 + random.nextInt(9000)));
                act.setStatus(targetStatus);  // ✅ Текущий статус
                act.setCreatedAt(Instant.now());
                act.setUpdatedAt(Instant.now());

                // ✅ РЕАЛИСТИЧНАЯ ИСТОРИЯ для статуса
                List<String> historyStatuses = statusHistory.getOrDefault(targetStatus, List.of(""));
                List<HistoryEntry> history = new ArrayList<>();

                String prevStatus = "";
                for (int h = 0; h < historyStatuses.size(); h++) {
                    String status = historyStatuses.get(h);
                    history.add(new HistoryEntry(
                            Instant.now().minusSeconds((historyStatuses.size() - h) * 86400L),  // даты назад
                            prevStatus,
                            status,
                            h == 0 ? "Загружено из 1С" : getStatusComment(status)
                    ));
                    prevStatus = status;
                }
                act.setHistory(history);

                if (sentSbis.equals(targetStatus) || sentCounterparty.equals(targetStatus)
                        || Arrays.asList(noResponse, signedReceived, closed, corrections, accounting).contains(targetStatus)) {
                    act.setSabySendId("SABY-" + randomHexString(12));
                }

                acts.add(act);
            }
        }

        actRepository.saveAll(acts);
        return Map.of("seeded", acts.size(), "message", "Создано " + acts.size() + " тестовых актов с историей");
    }

    private String getStatusComment(String status) {
        if (ActStatus.ОТПРАВЛЕНО_В_СБИС.getDisplayName().equals(status)) {
            return "Записано в СБИС";
        }
        if (ActStatus.ОТПРАВЛЕНО_КОНТРАГЕНТУ.getDisplayName().equals(status)) {
            return "Подписано нами и отправлено контрагенту, ожидание ответа";
        }
        if (ActStatus.ПОЛУЧЕН_ПОДПИСАННЫЙ.getDisplayName().equals(status)) {
            return "Получен подписанный документ из СБИС";
        }
        if (ActStatus.НЕТ_ОТВЕТА.getDisplayName().equals(status)) {
            return "Превышен срок ответа";
        }
        if (ActStatus.ЗАКРЫТ.getDisplayName().equals(status)) {
            return "Цикл сверки завершён";
        }
        if (ActStatus.КОРРЕКТИРОВКИ.getDisplayName().equals(status)) {
            return "Требуются корректировки";
        }
        if (ActStatus.В_РАБОТЕ_БУХГАЛТЕРИИ.getDisplayName().equals(status)) {
            return "Эскалация в бухгалтерию";
        }
        return "Смена статуса";
    }

    private String firstNonBlank(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) return 0.0;
        return Double.parseDouble(value.replace(",", "."));
    }

    private String randomHexString(int length) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int digit = random.nextInt(16);
            sb.append(Integer.toHexString(digit).toUpperCase());
        }
        return sb.toString();
    }

    private Element getFirstChildElementByTag(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    private String buildFlFullName(Element flElement) {
        Element fio = getFirstChildElementByTag(flElement, "ФИО");
        if (fio == null) return "";

        String surname = fio.getAttribute("Фамилия");
        String name = fio.getAttribute("Имя");
        String patronymic = fio.getAttribute("Отчество");

        List<String> parts = new ArrayList<>();
        if (surname != null && !surname.isBlank()) parts.add(surname);
        if (name != null && !name.isBlank()) parts.add(name);
        if (patronymic != null && !patronymic.isBlank()) parts.add(patronymic);

        return String.join(" ", parts);
    }

    private String buildPeriodFromXml(String startDateStr, String endDateStr) {
        if (startDateStr == null || startDateStr.isBlank()) return "";

        try {
            LocalDate start = LocalDate.parse(startDateStr, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            LocalDate end = endDateStr != null && !endDateStr.isBlank()
                    ? LocalDate.parse(endDateStr, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    : start;

            TemporalField quarterField = IsoFields.QUARTER_OF_YEAR;
            int quarter = start.get(quarterField);
            int year = start.getYear();

            // Если период не укладывается в квартал, показываем даты
            if (end.getYear() != year || end.get(quarterField) != quarter) {
                return start.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) +
                        " — " + end.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            }

            return quarter + " квартал " + year;
        } catch (DateTimeParseException e) {
            return startDateStr + (endDateStr != null ? " — " + endDateStr : "");
        }
    }

    private String buildIpFullName(Element ipElement) {
        Element fio = getFirstChildElementByTag(ipElement, "ФИО");
        if (fio == null) return "";

        String surname = fio.getAttribute("Фамилия");
        String name = fio.getAttribute("Имя");
        String patronymic = fio.getAttribute("Отчество");

        List<String> parts = new ArrayList<>();
        if (surname != null && !surname.isBlank()) parts.add(surname);
        if (name != null && !name.isBlank()) parts.add(name);
        if (patronymic != null && !patronymic.isBlank()) parts.add(patronymic);

        return String.join(" ", parts);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"-".equals(value.trim())) {
                return value.trim();
            }
        }
        return "0.00";
    }
}