package ru.lukin.edododo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import ru.lukin.edododo.config.SabyAppProperties;
import ru.lukin.edododo.dto.SabyAuthRequest;
import ru.lukin.edododo.model.ActDocument;
import ru.lukin.edododo.model.AttachmentFile;
import ru.lukin.edododo.model.FileDocument;
import ru.lukin.edododo.model.SabyAccount;
import ru.lukin.edododo.model.SabySettingsDocument;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Service
public class SabyServiceImpl implements SabyService {

    private static final Logger log = LoggerFactory.getLogger(SabyServiceImpl.class);

    private static final String DEFAULT_AUTH_URL = "https://online.sbis.ru/auth/service/";
    private static final String DOCUMENT_SERVICE_URL = "https://online.sbis.ru/service/?srv=1";
    private static final String SBIS_ONLINE_ORIGIN = "https://online.sbis.ru";
    private static final String PDF_SERVICE_URL = SBIS_ONLINE_ORIGIN + "/pdfservicepublic/service/";
    private static final String PDF_STORAGE_FINAL = "pdfservice_storage_final";
    private static final List<String> FILE_TRANSFER_BASES = List.of(
            "https://docs.saby.ru/file-transfer/service/",
            SBIS_ONLINE_ORIGIN + "/file-transfer/service/"
    );

    private record FileTransferRef(String transferId, String storage) {
    }
    private static final DateTimeFormatter RU_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final Pattern SIGNED_BY_COUNTERPARTY_STATE = Pattern.compile(
            "заверш|полностью\\s+подписан|подписан\\s+контрагент|получен\\s+контрагент|утвержден\\s+контрагент",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern AWAITING_COUNTERPARTY_STATE = Pattern.compile(
            "ожида.*контрагент|направлен\\s+контрагент|отправлен\\s+контрагент|на\\s+подпис",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private final SettingsServiceImpl settingsService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FileService fileService;
    private final SabyAppProperties sabyAppProperties;

    public SabyServiceImpl(
            SettingsServiceImpl settingsService,
            @Qualifier("sabyRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            FileService fileService,
            SabyAppProperties sabyAppProperties
    ) {
        this.settingsService = settingsService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.fileService = fileService;
        this.sabyAppProperties = sabyAppProperties;
    }

    // ─────────────────────────────────────────────────────────────
    // 🔐 АВТОРИЗАЦИЯ
    // ─────────────────────────────────────────────────────────────
    @Override
    public Map<String, Object> authenticate(SabyAuthRequest authRequest) {
        SabySettingsDocument currentSettings = settingsService.getSabySettings();

        String authUrl = currentSettings.getApiUrl() != null ? currentSettings.getApiUrl() : DEFAULT_AUTH_URL;
        if (!authUrl.contains("/auth/service")) {
            authUrl = DEFAULT_AUTH_URL;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("Логин", authRequest.getLogin());
        params.put("Пароль", authRequest.getPassword());
        if (authRequest.getAccountNumber() != null && !authRequest.getAccountNumber().isBlank()) {
            params.put("НомерАккаунта", authRequest.getAccountNumber());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", "СБИС.Аутентифицировать");
        payload.put("params", Map.of("Параметр", params));
        payload.put("id", 0);

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return buildError("Ошибка формирования запроса: " + e.getMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/json-rpc;charset=utf-8"));
        headers.set("User-Agent", "EDO-Control/1.0");
        headers.setAccept(List.of(MediaType.parseMediaType("application/json-rpc")));

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(authUrl, HttpMethod.POST, request, String.class);
            Map<String, Object> data = objectMapper.readValue(response.getBody(), Map.class);

            if (data != null && data.containsKey("result") && data.get("result") != null) {
                String sessionId = String.valueOf(data.get("result"));
                var account = settingsService.saveSabyAccount(
                        sessionId,
                        authRequest.getLogin(),
                        authRequest.getAccountNumber()
                );
                String masked = maskSessionId(sessionId);

                Map<String, Object> success = new HashMap<>();
                success.put("success", true);
                success.put("message", "Авторизация успешна. SessionID сохранён, режим переключён на Production.");
                success.put("session_id_preview", masked);
                success.put("account_id", account.getId());
                success.put("account", account.getDisplayName());
                success.put("timestamp", Instant.now().toString());
                return success;
            } else if (data != null && data.containsKey("error")) {
                Object err = data.get("error");
                String msg = err instanceof Map ? String.valueOf(((Map<?, ?>) err).get("message")) : String.valueOf(err);
                return buildError("Ошибка СБИС: " + msg);
            } else {
                String preview = data != null ?
                        objectMapper.writeValueAsString(data).substring(0, Math.min(300, objectMapper.writeValueAsString(data).length())) :
                        "пустой ответ";
                return buildError("Неожиданный ответ СБИС: " + preview);
            }

        } catch (HttpClientErrorException e) {
            return handleHttpError(e);
        } catch (ResourceAccessException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.toLowerCase().contains("timeout") || msg.toLowerCase().contains("read timed out")) {
                return buildError("Таймаут при подключении к СБИС. Попробуйте позже.");
            }
            return buildError("Не удалось подключиться к серверу СБИС. Проверьте URL и сетевое подключение.");
        } catch (JsonProcessingException e) {
            return buildError("Ошибка парсинга ответа: " + e.getMessage());
        } catch (Exception e) {
            return buildError("Ошибка: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 📤 ОТПРАВКА АКТА СВЕРКИ (по документации СБИС.ЗаписатьДокумент)
    // ─────────────────────────────────────────────────────────────
    @Override
    public Map<String, Object> sendActToSaby(ActDocument act, String accountId) {
        SabySettingsDocument settings = settingsService.getSabySettings();
        SabyAccount account = settingsService.requireSabyAccount(accountId);
        String sessionId = account.getSessionToken();

        if (sessionId == null || sessionId.isBlank()) {
            return buildError("SessionID не установлен. Сначала выполните авторизацию.");
        }

        String mode = settings.getMode();

        // Mock-режим для тестов
        if ("mock".equals(mode)) {
            return mockSignAndSendSuccess(act, account.getId());
        }

        // === PRODUCTION: отправка через СБИС.ЗаписатьДокумент ===

        // 1. Читаем файл акта и конвертируем в Base64
// В sendActToSaby, при сборе вложений:
        List<AttachmentFile> files = new ArrayList<>();

        List<FileDocument> fileDocs = fileService.getActFiles(act.getId());
        for (FileDocument fd : fileDocs) {
            if (fd.isDeleted()) continue;

            // ✅ Берём ТОЛЬКО PDF
            if (fd.getOriginalFilename() != null &&
                    fd.getOriginalFilename().toLowerCase().endsWith(".pdf")) {

                try {
                    byte[] content = fileService.downloadFile(fd.getId());
                    files.add(new AttachmentFile(
                            fd.getOriginalFilename(),
                            content,
                            fd.getContentType()
                    ));
                } catch (Exception e) {
                    System.err.println("Не удалось прочитать файл " + fd.getId() + ": " + e.getMessage());
                }
            }
        }

        if (files.isEmpty()) {
            return buildError("Не найдено PDF-вложение. Прикрепите файл .pdf к акту перед отправкой.");
        }

        // 2. Парсим период
        LocalDate[] period = parsePeriod(act.getPeriod());

        // 3. Формируем запрос с массивом вложений
        Map<String, Object> rpcBody = buildWriteDocumentRequest(act, files, period);

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(rpcBody);
        } catch (JsonProcessingException e) {
            return buildError("Ошибка сериализации запроса: " + e.getMessage());
        }

        // 4. Заголовки (важно: X-SBISSessionId с маленькой 'd' в конце)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/json-rpc;charset=utf-8"));
        headers.set("X-SBISSessionId", sessionId);
        headers.set("User-Agent", "EDO-Control/1.0");
        headers.setAccept(List.of(MediaType.parseMediaType("application/json-rpc")));

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    DOCUMENT_SERVICE_URL,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);

            if (responseBody != null && responseBody.containsKey("result")) {
                Map<String, Object> result = (Map<String, Object>) responseBody.get("result");
                String docId = String.valueOf(result.get("Идентификатор"));
                String status = extractStatus(result);
                String link = String.valueOf(result.get("СсылкаДляНашаОрганизация"));

                Map<String, Object> signResult = signAndSendToCounterparty(sessionId, docId);
                if (!Boolean.TRUE.equals(signResult.get("success"))) {
                    Map<String, Object> err = buildError(
                            "Документ создан в СБИС (ID: " + docId + "), но подписание/отправка не выполнены: "
                                    + signResult.get("message")
                    );
                    err.put("document_id", docId);
                    return err;
                }

                Map<String, Object> success = new HashMap<>();
                success.put("success", true);
                success.put("document_id", docId);
                success.put("mode", "production");
                success.put("message", "Акт подписан и отправлен контрагенту через СБИС");
                success.put("act_number", act.getActNumber());
                success.put("saby_link", link);
                success.put("status", signResult.getOrDefault("status", status));
                success.put("signed", true);
                success.put("sent_to_counterparty", true);
                success.put("deferred_cert_type", sabyAppProperties.getDeferredCertType());
                success.put("account_id", account.getId());
                success.put("timestamp", Instant.now().toString());
                return success;

            } else if (responseBody != null && responseBody.containsKey("error")) {
                return buildError("СБИС: " + responseBody.get("error"));
            }
            return buildError("Неожиданный формат ответа от СБИС");

        } catch (HttpClientErrorException e) {
            return handleHttpError(e);
        } catch (ResourceAccessException e) {
            return buildError("Ошибка соединения с СБИС: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } catch (Exception e) {
            return buildError("Ошибка: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 🔧 ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ─────────────────────────────────────────────────────────────
    private Map<String, Object> buildWriteDocumentRequest(ActDocument act, List<AttachmentFile> files, LocalDate[] period) {

        List<Map<String, Object>> attachments = new ArrayList<>();

        for (AttachmentFile file : files) {
            String fileName = file.getOriginalName();
            byte[] content = file.getContent();

            Map<String, Object> fileObj = Map.of(
                    "Имя", fileName,
                    "ДвоичныеДанные", Base64.getEncoder().encodeToString(content)
            );

            Map<String, Object> attachment = new HashMap<>();
            attachment.put("Идентификатор", UUID.randomUUID().toString());
            attachment.put("Название", "Акт сверки " + act.getActNumber());
            attachment.put("Служебный", "Нет");
            attachment.put("Файл", fileObj);

            // ❌ НЕ указываем Тип/Подтип/ВерсияФормата — это неформализованное вложение
            // СБИС примет его и покажет как PDF

            attachments.add(attachment);
        }

        // Документ
        Map<String, Object> document = new HashMap<>();
        document.put("Тип", "АктСверИсх");  // ✅ Тип самого документа (не вложения!)
        document.put("Номер", act.getActNumber());
        document.put("Дата", formatDate(parseDate(act.getFormationDate())));
        document.put("НачалоПериода", formatDate(period[0]));
        document.put("КонецПериода", formatDate(period[1]));
        document.put("Сумма", String.valueOf(act.getAmount()).replace(",", "."));
        document.put("Примечание", "Акт сверки за " + act.getPeriod());

//        document.put("НашаОрганизация", buildOrganization(act, true));   // наша компания
                document.put("НашаОрганизация", buildOrganization(
                "2635264264",
                "263501001",
                "ООО СТ-6"
        ));
        document.put("Контрагент", buildOrganization(act, false));       // контрагент

//        if (1 == 1){
//            throw new RuntimeException();
//        }
        document.put("Вложение", attachments);
        document.put("Редакция", List.of(Map.of("ПримечаниеИС", "edo-dodo:" + act.getId())));

        // JSON-RPC
        Map<String, Object> rpc = new HashMap<>();
        rpc.put("jsonrpc", "2.0");
        rpc.put("method", "СБИС.ЗаписатьДокумент");
        rpc.put("params", Map.of("Документ", document));
        rpc.put("id", 1);

        return rpc;
    }
//    private Map<String, Object> buildWriteDocumentRequest(ActDocument act, String base64Content, LocalDate[] period) {
//        // Вложение
//        Map<String, Object> file = Map.of(
//                "Имя", act.getActNumber() + ".pdf",
//                "ДвоичныеДанные", base64Content
//        );
//
//        Map<String, Object> attachment = new HashMap<>();
//        attachment.put("Идентификатор", UUID.randomUUID().toString());
//        attachment.put("Название", "Акт сверки " + act.getActNumber() + ".pdf");
//        attachment.put("Служебный", "Нет");
//        attachment.put("Файл", file);
//
//        // Документ
//        Map<String, Object> document = new HashMap<>();
//        document.put("Тип", "АктСверИсх");
//        document.put("Номер", act.getActNumber());
//        document.put("Дата", formatDate(parseDate(act.getFormationDate())));
//        document.put("НачалоПериода", formatDate(period[0]));
//        document.put("КонецПериода", formatDate(period[1]));
//        document.put("Сумма", String.valueOf(act.getAmount()).replace(",", "."));
//        document.put("Примечание", "Акт сверки за " + act.getPeriod());
//
//        // Наша организация
//        document.put("НашаОрганизация", buildOrganization(
//                "2635264264",
//                "263501001",
//                "ООО СТ-6"
//        ));
//
//        // Контрагент
//        document.put("Контрагент", buildOrganization(act.getInn(), act.getKpp(), act.getCounterparty()));
//
//        document.put("Вложение", List.of(attachment));
//        document.put("Редакция", List.of(Map.of("ПримечаниеИС", "edo-dodo:" + act.getId())));
//
//        // JSON-RPC обёртка
//        Map<String, Object> rpc = new HashMap<>();
//        rpc.put("jsonrpc", "2.0");
//        rpc.put("method", "СБИС.ЗаписатьДокумент");
//        rpc.put("params", Map.of("Документ", document));
//        rpc.put("id", 1);
//
//        return rpc;
//    }

private Map<String, Object> buildOrganization(ActDocument act, boolean isOurCompany) {
    String inn = isOurCompany ? act.getOurCompanyInn() : act.getCounterpartyInn();
    String kpp = isOurCompany ? act.getOurCompanyKpp() : act.getCounterpartyKpp();
    String name = isOurCompany ? act.getOurCompanyName() : act.getCounterparty();
    String type = isOurCompany ? "UL" : act.getCounterpartyType();

    if (inn == null || inn.isBlank()) return Map.of();

    // ИП или физлицо (12 знаков ИНН или явный тип)
    if (inn.length() == 12 || "IP".equals(type) || "FL".equals(type)) {
        Map<String, String> svfl = new HashMap<>();
        svfl.put("ИНН", inn);
        svfl.put("КодСтраны", "643");

        // Если есть раздельные ФИО — используем их
        if (!isOurCompany) {
            if (act.getCounterpartyLastName() != null && !act.getCounterpartyLastName().isBlank()) {
                svfl.put("Фамилия", act.getCounterpartyLastName());
            }
            if (act.getCounterpartyFirstName() != null && !act.getCounterpartyFirstName().isBlank()) {
                svfl.put("Имя", act.getCounterpartyFirstName());
            }
            if (act.getCounterpartyPatronymic() != null && !act.getCounterpartyPatronymic().isBlank()) {
                svfl.put("Отчество", act.getCounterpartyPatronymic());
            }
        } else if (name != null && !name.isBlank() && name.contains(" ")) {
            // Парсим полное имя, если нет раздельных полей
            String[] parts = name.trim().split("\\s+", 3);
            if (parts.length >= 1) svfl.put("Фамилия", parts[0]);
            if (parts.length >= 2) svfl.put("Имя", parts[1]);
            if (parts.length >= 3) svfl.put("Отчество", parts[2]);
        }

        return Map.of("СвФЛ", svfl);
    }
    // Юрлицо (10 знаков ИНН)
    else {
        Map<String, String> svul = new HashMap<>();
        svul.put("ИНН", inn);
        if (kpp != null && !kpp.isBlank()) svul.put("КПП", kpp);
        if (name != null && !name.isBlank()) svul.put("Название", name);
        svul.put("КодСтраны", "643");
        return Map.of("СвЮЛ", svul);
    }
}

    private String formatDate(LocalDate date) {
        return date != null ? date.format(RU_DATE_FORMAT) : null;
    }

    private LocalDate[] parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            LocalDate now = LocalDate.now();
            return new LocalDate[]{now.withDayOfMonth(1), now.withDayOfMonth(now.lengthOfMonth())};
        }
        try {
            int year = Integer.parseInt(period.substring(period.length() - 4));
            if (period.contains("1 квартал")) {
                return new LocalDate[]{LocalDate.of(year, 1, 1), LocalDate.of(year, 3, 31)};
            } else if (period.contains("2 квартал")) {
                return new LocalDate[]{LocalDate.of(year, 4, 1), LocalDate.of(year, 6, 30)};
            } else if (period.contains("3 квартал")) {
                return new LocalDate[]{LocalDate.of(year, 7, 1), LocalDate.of(year, 9, 30)};
            } else if (period.contains("4 квартал")) {
                return new LocalDate[]{LocalDate.of(year, 10, 1), LocalDate.of(year, 12, 31)};
            }
            LocalDate now = LocalDate.of(year, 1, 1);
            return new LocalDate[]{now.withDayOfMonth(1), now.withDayOfMonth(now.lengthOfMonth())};
        } catch (Exception e) {
            LocalDate now = LocalDate.now();
            return new LocalDate[]{now.withDayOfMonth(1), now.withDayOfMonth(now.lengthOfMonth())};
        }
    }

    private String extractStatus(Map<String, Object> result) {
        Object state = result.get("Состояние");
        if (state instanceof Map) {
            return String.valueOf(((Map<?, ?>) state).get("Название"));
        }
        return "Неизвестно";
    }

    // ✅ Ваши вспомогательные методы (без изменений)
    private Map<String, Object> handleHttpError(HttpClientErrorException e) {
        String errorBody = e.getResponseBodyAsString();
        String readable = e.getStatusCode().value() + " " + e.getStatusText();

        try {
            Map<String, Object> errorJson = objectMapper.readValue(errorBody, Map.class);
            if (errorJson.containsKey("error")) {
                Object err = errorJson.get("error");
                if (err instanceof Map) {
                    readable = "СБИС (" + e.getStatusCode().value() + "): " + ((Map<?, ?>) err).get("message");
                } else {
                    readable = "СБИС (" + e.getStatusCode().value() + "): " + err;
                }
            }
        } catch (JsonProcessingException ignored) {
            if (errorBody != null && !errorBody.isBlank()) {
                readable += " | " + errorBody.substring(0, Math.min(150, errorBody.length()));
            }
        }
        return buildError(readable);
    }

    private String maskSessionId(String sessionId) {
        if (sessionId == null || sessionId.length() <= 12) return "••••";
        return sessionId.substring(0, 8) + "••••" + sessionId.substring(sessionId.length() - 4);
    }

    private String resolveSessionId(String accountId) {
        if (accountId != null && !accountId.isBlank()) {
            return settingsService.getSabyAccount(accountId)
                    .map(SabyAccount::getSessionToken)
                    .orElse(null);
        }

        SabySettingsDocument settings = settingsService.getSabySettings();
        if (settings.getApiToken() != null && !settings.getApiToken().isBlank()) {
            return settings.getApiToken();
        }

        List<SabyAccount> accounts = settings.getAccounts();
        if (accounts != null && accounts.size() == 1) {
            return accounts.get(0).getSessionToken();
        }
        return null;
    }

    private Map<String, Object> buildError(String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("success", false);
        err.put("message", message != null ? message : "Unknown error");
        err.put("timestamp", Instant.now().toString());
        return err;
    }

    @Override
    public Map<String, Object> readDocument(String documentId, String accountId) {
        SabySettingsDocument settings = settingsService.getSabySettings();
        String sessionId = resolveSessionId(accountId);
        if (sessionId == null || sessionId.isBlank()) {
            return buildError("SessionID не установлен");
        }
        if ("mock".equals(settings.getMode())) {
            return mockReadDocument(documentId);
        }

        Map<String, Object> params = Map.of(
                "Документ", Map.of("Идентификатор", documentId)
        );
        Map<String, Object> rpcResult = invokeRpc(sessionId, "СБИС.ПрочитатьДокумент", params, 3);
        if (!Boolean.TRUE.equals(rpcResult.get("success"))) {
            return rpcResult;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> document = (Map<String, Object>) rpcResult.get("result");
        Map<String, Object> success = new HashMap<>();
        success.put("success", true);
        success.put("document", document);
        success.put("status", extractStatus(document));
        return success;
    }

    @Override
    public boolean isCounterpartySigned(Map<String, Object> document) {
        if (document == null) {
            return false;
        }

        Object stateObj = document.get("Состояние");
        if (stateObj instanceof Map<?, ?> state) {
            String name = String.valueOf(state.get("Название"));
            if (name != null && !"null".equals(name)) {
                if (SIGNED_BY_COUNTERPARTY_STATE.matcher(name).find()) {
                    return true;
                }
                if (AWAITING_COUNTERPARTY_STATE.matcher(name).find()) {
                    return false;
                }
            }
            String code = String.valueOf(state.get("Код"));
            if ("7".equals(code) || "9".equals(code)) {
                return true;
            }
        }

        return hasCounterpartyCompletedStage(document);
    }

    @Override
    public int syncNewAttachments(ActDocument act, Map<String, Object> document, String accountId) {
        if (document == null) {
            return 0;
        }
        String sessionId = resolveSessionId(accountId != null ? accountId : act.getSabyAccountId());
        int saved = 0;

        for (Object attachmentObj : asList(document.get("Вложение"))) {
            if (!(attachmentObj instanceof Map<?, ?> attachment)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> att = (Map<String, Object>) attachment;
            if ("Да".equals(String.valueOf(att.get("Служебный")))) {
                continue;
            }
            Object fileObj = att.get("Файл");
            if (!(fileObj instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> file = (Map<String, Object>) fileObj;
            String fileName = file.get("Имя") != null ? String.valueOf(file.get("Имя")) : String.valueOf(att.get("Название"));
            if (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                continue;
            }
            byte[] content = extractFileBytes(sessionId, file);
            if (content == null || content.length == 0) {
                continue;
            }
            String contentType = guessContentType(fileName);
            if (fileService.saveActFileIfNew(act.getId(), fileName, content, contentType)) {
                saved++;
            }
        }
        return saved;
    }

    @Override
    public boolean syncStampedPdf(ActDocument act, Map<String, Object> document, String accountId) {
        if (document == null) {
            return false;
        }
        SabySettingsDocument settings = settingsService.getSabySettings();
        if ("mock".equals(settings.getMode())) {
            return false;
        }
        String sessionId = resolveSessionId(accountId != null ? accountId : act.getSabyAccountId());
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }

        String pdfLink = findLatestStampedPdfLink(document);
        byte[] pdfContent = downloadStampedPdfContent(sessionId, document, pdfLink);
        if (pdfContent == null || pdfContent.length == 0) {
            log.warn("Не удалось получить PDF со штампом для акта {} (документ СБИС: {})",
                    act.getId(), act.getSabySendId());
            return false;
        }

        String filename = resolveStampedPdfFilename(document);
        return fileService.replaceActPdfFromSbis(act.getId(), pdfContent, filename);
    }

    private byte[] downloadStampedPdfContent(String sessionId, Map<String, Object> document, String preferredLink) {
        for (FileTransferRef ref : collectFileTransferRefs(document, preferredLink)) {
            byte[] pdf = downloadPdfViaFileTransferWithRetry(sessionId, ref);
            if (isPdfContent(pdf)) {
                log.debug("PDF со штампом получен через FileTransfer.Download (storage={})", ref.storage());
                return pdf;
            }
        }

        List<String> candidates = collectPdfDownloadCandidates(document, preferredLink);
        for (String url : candidates) {
            byte[] pdf = downloadPdfByLinkWithRetry(sessionId, url);
            if (isPdfContent(pdf)) {
                log.debug("PDF со штампом получен: {}", abbreviateUrl(url));
                return pdf;
            }
        }

        byte[] fromArchive = downloadPdfFromDocumentArchive(sessionId, document);
        if (isPdfContent(fromArchive)) {
            log.debug("PDF извлечён из архива СБИС");
            return fromArchive;
        }

        return null;
    }

    private byte[] downloadPdfViaFileTransferWithRetry(String sessionId, FileTransferRef ref) {
        if (!isValidFileTransferRef(ref)) {
            return null;
        }
        int maxAttempts = 5;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            byte[] content = downloadViaFileTransfer(sessionId, ref);
            if (isPdfContent(content)) {
                return content;
            }
            if (isInvalidFileTransferIdError(content)) {
                log.debug("FileTransfer.Download: некорректный id ({}), повтор не нужен", ref.transferId());
                return null;
            }
            if (isPdfGenerationPending(content)) {
                sleepQuietly(4000);
                continue;
            }
            if (attempt < maxAttempts - 1) {
                sleepQuietly(4000);
            }
        }
        return null;
    }

    private byte[] downloadViaFileTransfer(String sessionId, FileTransferRef ref) {
        if (!isValidFileTransferRef(ref)) {
            return null;
        }
        for (String base : FILE_TRANSFER_BASES) {
            String url = buildFileTransferDownloadUrl(base, ref.transferId(), ref.storage());
            byte[] content = downloadByLink(sessionId, url);
            if (isPdfContent(content)) {
                return content;
            }
        }
        return null;
    }

    private String buildFileTransferDownloadUrl(String baseUrl, String transferId, String storage) {
        if (!isValidFileTransferId(transferId) || storage == null || storage.isBlank()) {
            return null;
        }
        try {
            Map<String, String> payload = Map.of("id", transferId, "storage", storage);
            String json = objectMapper.writeValueAsString(payload);
            String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            String params = URLEncoder.encode(encoded, StandardCharsets.UTF_8);
            long requestId = System.currentTimeMillis() % 100_000_000L;
            return baseUrl + "?id=" + requestId
                    + "&method=FileTransfer.Download"
                    + "&protocol=6"
                    + "&params=" + params;
        } catch (JsonProcessingException e) {
            log.debug("Не удалось собрать URL FileTransfer: {}", e.getMessage());
            return null;
        }
    }

    private List<FileTransferRef> collectFileTransferRefs(Map<String, Object> document, String preferredLink) {
        LinkedHashSet<FileTransferRef> refs = new LinkedHashSet<>();

        FileTransferRef fromPreferred = parseFileTransferFromUrl(preferredLink);
        if (fromPreferred != null) {
            refs.add(fromPreferred);
        }

        collectFileTransferRefsRecursive(document, refs);
        refs.addAll(buildFileTransferRefsFromDocument(document));

        refs.removeIf(ref -> !isValidFileTransferRef(ref));
        return new ArrayList<>(refs);
    }

    private void collectFileTransferRefsRecursive(Object node, Set<FileTransferRef> refs) {
        if (node == null) {
            return;
        }
        if (node instanceof String str) {
            FileTransferRef parsed = parseFileTransferFromUrl(str);
            if (parsed != null) {
                refs.add(parsed);
            }
            return;
        }
        if (node instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapObj = (Map<String, Object>) map;
            Object storage = firstMapValue(mapObj, "storage", "Storage", "Хранилище");
            Object transferId = firstMapValue(mapObj, "id", "Id");
            if (storage != null && transferId != null
                    && String.valueOf(storage).toLowerCase(Locale.ROOT).contains("pdfservice")
                    && isValidFileTransferId(String.valueOf(transferId))) {
                refs.add(new FileTransferRef(String.valueOf(transferId), String.valueOf(storage)));
            }
            for (Object value : mapObj.values()) {
                collectFileTransferRefsRecursive(value, refs);
            }
            return;
        }
        if (node instanceof List<?> list) {
            for (Object item : list) {
                collectFileTransferRefsRecursive(item, refs);
            }
        }
    }

    private List<FileTransferRef> buildFileTransferRefsFromDocument(Map<String, Object> document) {
        List<FileTransferRef> refs = new ArrayList<>();
        List<String> revisionIds = collectRevisionIds(document);
        List<String> diskFileIds = collectDiskFileIds(document);

        for (String revisionId : revisionIds) {
            for (String diskFileId : diskFileIds) {
                refs.add(new FileTransferRef(revisionId + "+" + diskFileId, PDF_STORAGE_FINAL));
            }
        }

        for (String diskFileId : diskFileIds) {
            String docId = document.get("Идентификатор") != null ? String.valueOf(document.get("Идентификатор")) : null;
            if (docId != null && !docId.isBlank()) {
                refs.add(new FileTransferRef(docId + "+" + diskFileId, PDF_STORAGE_FINAL));
            }
        }

        return refs;
    }

    private List<String> collectRevisionIds(Map<String, Object> document) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        collectRevisionIdsFromNode(document.get("Редакция"), ids);
        for (Object eventObj : asList(document.get("Событие"))) {
            if (eventObj instanceof Map<?, ?> event) {
                collectRevisionIdsFromNode(((Map<?, ?>) event).get("Редакция"), ids);
            }
        }
        return new ArrayList<>(ids);
    }

    private void collectRevisionIdsFromNode(Object revisionNode, Set<String> ids) {
        if (revisionNode instanceof Map<?, ?> rev && rev.get("Идентификатор") != null) {
            String id = String.valueOf(rev.get("Идентификатор"));
            if (!id.isBlank() && !"null".equals(id)) {
                ids.add(id);
            }
        }
        if (revisionNode instanceof List<?> list) {
            for (Object item : list) {
                collectRevisionIdsFromNode(item, ids);
            }
        }
    }

    private List<String> collectDiskFileIds(Map<String, Object> document) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        collectDiskFileIdsFromAttachments(asList(document.get("Вложение")), ids);
        for (Object eventObj : asList(document.get("Событие"))) {
            if (eventObj instanceof Map<?, ?> event) {
                collectDiskFileIdsFromAttachments(asList(event.get("Вложение")), ids);
            }
        }
        return new ArrayList<>(ids);
    }

    private void collectDiskFileIdsFromAttachments(List<Object> attachments, Set<String> ids) {
        for (Object attachmentObj : attachments) {
            if (!(attachmentObj instanceof Map<?, ?> att)) {
                continue;
            }
            if ("Да".equals(String.valueOf(att.get("Служебный")))) {
                continue;
            }
            Object fileObj = att.get("Файл");
            if (fileObj instanceof Map<?, ?> file) {
                String diskId = extractDiskFileIdFromLink(resolveLinkValue(file.get("Ссылка")));
                if (diskId != null) {
                    ids.add(diskId);
                }
            }
            String pdfLink = resolveLinkValue(att.get("СсылкаНаPDF"));
            String diskFromPdf = extractDiskFileIdFromLink(pdfLink);
            if (diskFromPdf != null) {
                ids.add(diskFromPdf);
            }
        }
    }

    private String extractDiskFileIdFromLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        int marker = link.indexOf("/disk/api/v1/");
        if (marker < 0) {
            return null;
        }
        String rest = link.substring(marker + "/disk/api/v1/".length());
        int queryIdx = rest.indexOf('?');
        if (queryIdx >= 0) {
            rest = rest.substring(0, queryIdx);
        }
        int slashIdx = rest.indexOf('/');
        if (slashIdx >= 0) {
            rest = rest.substring(0, slashIdx);
        }
        return rest.isBlank() ? null : rest;
    }

    private FileTransferRef parseFileTransferFromUrl(String url) {
        if (url == null || !url.contains("file-transfer/service")) {
            return null;
        }
        int paramsIdx = url.indexOf("params=");
        if (paramsIdx < 0) {
            return null;
        }
        String encoded = url.substring(paramsIdx + "params=".length());
        int amp = encoded.indexOf('&');
        if (amp >= 0) {
            encoded = encoded.substring(0, amp);
        }
        try {
            String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            byte[] raw = Base64.getDecoder().decode(decoded);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(raw, Map.class);
            Object id = payload.get("id");
            Object storage = payload.get("storage");
            if (id == null || storage == null || !isValidFileTransferId(String.valueOf(id))) {
                return null;
            }
            return new FileTransferRef(String.valueOf(id), String.valueOf(storage));
        } catch (Exception e) {
            log.debug("Не удалось разобрать params FileTransfer из URL: {}", e.getMessage());
            return null;
        }
    }

    private Object firstMapValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank() && !"null".equals(String.valueOf(value))) {
                return value;
            }
        }
        return null;
    }

    private List<String> collectPdfDownloadCandidates(Map<String, Object> document, String preferredLink) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();

        addPdfCandidate(urls, resolveLinkValue(document.get("СсылкаНаPDF")));
        addPdfCandidate(urls, preferredLink);

        for (Object attachmentObj : asList(document.get("Вложение"))) {
            if (!(attachmentObj instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> att = (Map<String, Object>) attachmentObj;
            if ("Да".equals(String.valueOf(att.get("Служебный")))) {
                continue;
            }
            addPdfCandidate(urls, resolveLinkValue(att.get("СсылкаНаPDF")));
        }

        for (Object eventObj : asList(document.get("Событие"))) {
            if (!(eventObj instanceof Map<?, ?> event)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> eventMap = (Map<String, Object>) event;
            for (Object attachmentObj : asList(eventMap.get("Вложение"))) {
                if (!(attachmentObj instanceof Map<?, ?> att)) {
                    continue;
                }
                if ("Да".equals(String.valueOf(att.get("Служебный")))) {
                    continue;
                }
                addPdfCandidate(urls, resolveLinkValue(att.get("СсылкаНаPDF")));
            }
        }

        for (String built : buildPdfServiceCandidateUrls(document)) {
            addPdfCandidate(urls, built);
        }

        return new ArrayList<>(urls);
    }

    private void addPdfCandidate(Set<String> urls, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank() || "null".equals(rawUrl)) {
            return;
        }
        if (rawUrl.contains("file-transfer/service")) {
            if (isValidFileTransferDownloadUrl(rawUrl.trim())) {
                urls.add(rawUrl.trim());
            } else {
                log.debug("Пропуск неполной ссылки FileTransfer (нет id редакции+файла): {}", abbreviateUrl(rawUrl));
            }
            return;
        }
        String normalized = normalizeSbisDownloadUrl(rawUrl);
        if (isIncompletePdfServiceUrl(normalized)) {
            log.debug("Пропуск неполной ссылки PDF СБИС: {}", normalized);
            return;
        }
        urls.add(normalized);
    }

    private List<String> buildPdfServiceCandidateUrls(Map<String, Object> document) {
        List<String> urls = new ArrayList<>();
        String docId = document.get("Идентификатор") != null ? String.valueOf(document.get("Идентификатор")) : null;
        String revisionId = extractRevisionId(document);
        String attachmentId = findPrimaryAttachmentId(document);

        if (docId == null || docId.isBlank()) {
            return urls;
        }

        List<Map<String, Object>> payloads = new ArrayList<>();
        payloads.add(Map.of("Идентификатор", docId));
        payloads.add(Map.of("Документ", Map.of("Идентификатор", docId)));
        if (revisionId != null) {
            payloads.add(Map.of(
                    "ИдентификаторДокумента", docId,
                    "ИдентификаторРедакции", revisionId
            ));
            payloads.add(Map.of("Документ", Map.of(
                    "Идентификатор", docId,
                    "Редакция", Map.of("Идентификатор", revisionId)
            )));
        }
        if (attachmentId != null) {
            payloads.add(Map.of(
                    "ИдентификаторДокумента", docId,
                    "ИдентификаторВложения", attachmentId
            ));
            payloads.add(Map.of(
                    "Идентификатор", docId,
                    "ИдентификаторВложения", attachmentId
            ));
        }

        for (Map<String, Object> payload : payloads) {
            try {
                String json = objectMapper.writeValueAsString(payload);
                String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
                urls.add(PDF_SERVICE_URL + "?param=" + encoded);
                urls.add(PDF_SERVICE_URL + "?data=" + encoded);
                urls.add(PDF_SERVICE_URL + "?request=" + encoded);
            } catch (JsonProcessingException e) {
                log.debug("Не удалось собрать запрос PDF СБИС: {}", e.getMessage());
            }
        }
        return urls;
    }

    private String findPrimaryAttachmentId(Map<String, Object> document) {
        for (Object attachmentObj : asList(document.get("Вложение"))) {
            if (!(attachmentObj instanceof Map<?, ?> att)) {
                continue;
            }
            if ("Да".equals(String.valueOf(att.get("Служебный")))) {
                continue;
            }
            Object id = att.get("Идентификатор");
            if (id != null && !String.valueOf(id).isBlank()) {
                return String.valueOf(id);
            }
        }
        return null;
    }

    private byte[] downloadPdfFromDocumentArchive(String sessionId, Map<String, Object> document) {
        String archiveLink = resolveLinkValue(document.get("СсылкаНаАрхив"));
        if (archiveLink == null) {
            return null;
        }
        byte[] zip = downloadByLink(sessionId, archiveLink);
        return extractPdfFromZip(zip);
    }

    private byte[] extractPdfFromZip(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length < 4) {
            return null;
        }
        if (isPdfContent(zipBytes)) {
            return zipBytes;
        }

        byte[] bestPdf = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".pdf")) {
                    continue;
                }
                byte[] entryBytes = readZipEntryBytes(zis);
                if (isPdfContent(entryBytes) && (bestPdf == null || entryBytes.length > bestPdf.length)) {
                    bestPdf = entryBytes;
                }
            }
        } catch (Exception e) {
            log.debug("Не удалось извлечь PDF из архива СБИС: {}", e.getMessage());
        }
        return bestPdf;
    }

    private byte[] readZipEntryBytes(ZipInputStream zis) throws java.io.IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = zis.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private String resolveLinkValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str.isBlank() ? null : str;
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapObj = (Map<String, Object>) map;
            for (String key : List.of("Ссылка", "Href", "href", "URL", "Url", "url")) {
                Object nested = mapObj.get(key);
                if (nested != null && !String.valueOf(nested).isBlank()) {
                    return String.valueOf(nested);
                }
            }
            Object transferId = mapObj.get("id");
            Object storage = mapObj.get("storage");
            if (transferId != null && storage != null
                    && isValidFileTransferId(String.valueOf(transferId))) {
                String built = buildFileTransferDownloadUrl(
                        FILE_TRANSFER_BASES.get(0),
                        String.valueOf(transferId),
                        String.valueOf(storage)
                );
                if (built != null) {
                    return built;
                }
            }
        }
        return String.valueOf(value);
    }

    private boolean isIncompletePdfServiceUrl(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.contains("pdfservicepublic")) {
            return false;
        }
        if (url.contains("?") && url.indexOf('?') < url.length() - 1) {
            return false;
        }
        int serviceIdx = lower.indexOf("/service/");
        if (serviceIdx >= 0 && url.length() > serviceIdx + "/service/".length()) {
            return false;
        }
        return lower.endsWith("/service/") || lower.endsWith("/service");
    }

    private String normalizeSbisDownloadUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String normalized = url.trim();
        normalized = normalized.replace("https://disk.sbis.ru", SBIS_ONLINE_ORIGIN);
        normalized = normalized.replace("http://disk.sbis.ru", SBIS_ONLINE_ORIGIN);
        normalized = normalized.replace("https://cdn-disk.sbis.ru", SBIS_ONLINE_ORIGIN);
        normalized = normalized.replace("https://disk.saby.ru", SBIS_ONLINE_ORIGIN);
        normalized = normalized.replace("https://cdn-disk.saby.ru", SBIS_ONLINE_ORIGIN);
        return normalized;
    }

    private String abbreviateUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.length() <= 120 ? url : url.substring(0, 120) + "...";
    }

    private String findLatestStampedPdfLink(Map<String, Object> document) {
        List<String> candidates = collectPdfDownloadCandidates(document, null);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private String resolveStampedPdfFilename(Map<String, Object> document) {
        for (Object attachmentObj : asList(document.get("Вложение"))) {
            if (!(attachmentObj instanceof Map<?, ?> attachment)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> att = (Map<String, Object>) attachment;
            if ("Да".equals(String.valueOf(att.get("Служебный")))) {
                continue;
            }
            Object fileObj = att.get("Файл");
            if (fileObj instanceof Map<?, ?> fileMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> file = (Map<String, Object>) fileMap;
                String name = file.get("Имя") != null ? String.valueOf(file.get("Имя")) : null;
                if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    return name;
                }
            }
            String title = att.get("Название") != null ? String.valueOf(att.get("Название")) : null;
            if (title != null && !title.isBlank() && !"null".equals(title)) {
                return title.endsWith(".pdf") ? title : title + ".pdf";
            }
        }
        return null;
    }

    private byte[] downloadPdfByLinkWithRetry(String sessionId, String url) {
        if (url != null && url.contains("file-transfer/service") && !isValidFileTransferDownloadUrl(url)) {
            return null;
        }
        int maxAttempts = 5;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            byte[] content = downloadByLink(sessionId, url);
            if (isPdfContent(content)) {
                return content;
            }
            if (isPdfGenerationPending(content)) {
                sleepQuietly(4000);
                continue;
            }
            byte[] postContent = postPdfServiceBody(sessionId, url);
            if (isPdfContent(postContent)) {
                return postContent;
            }
            if (attempt < maxAttempts - 1) {
                sleepQuietly(4000);
            }
        }
        return null;
    }

    private boolean isPdfGenerationPending(byte[] content) {
        if (content == null || content.length == 0) {
            return false;
        }
        String text = new String(content, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return text.contains("1aa0000f1002")
                || text.contains("асинхрон")
                || text.contains("формируется");
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private byte[] postPdfServiceBody(String sessionId, String url) {
        if (!url.contains("pdfservicepublic")) {
            return null;
        }
        String body = extractQueryParamValue(url, "params");
        if (body == null) {
            body = extractQueryParamValue(url, "param");
        }
        if (body == null) {
            body = extractQueryParamValue(url, "data");
        }
        if (body == null) {
            body = extractQueryParamValue(url, "request");
        }
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            HttpHeaders headers = buildDownloadHeaders(sessionId);
            headers.setContentType(MediaType.TEXT_PLAIN);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    URI.create(PDF_SERVICE_URL),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    byte[].class
            );
            return response.getBody();
        } catch (Exception e) {
            log.debug("POST PDF СБИС не удался: {}", e.getMessage());
            return null;
        }
    }

    private String extractQueryParamValue(String url, String paramName) {
        String marker = paramName + "=";
        int idx = url.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String value = url.substring(idx + marker.length());
        int amp = value.indexOf('&');
        if (amp >= 0) {
            value = value.substring(0, amp);
        }
        return value.isBlank() ? null : value;
    }

    private boolean isValidFileTransferRef(FileTransferRef ref) {
        return ref != null
                && ref.storage() != null && !ref.storage().isBlank()
                && isValidFileTransferId(ref.transferId());
    }

    private boolean isValidFileTransferId(String transferId) {
        if (transferId == null || transferId.isBlank() || "null".equals(transferId)) {
            return false;
        }
        int plusIdx = transferId.indexOf('+');
        if (plusIdx <= 0 || plusIdx >= transferId.length() - 1) {
            return false;
        }
        String revisionPart = transferId.substring(0, plusIdx).trim();
        String filePart = transferId.substring(plusIdx + 1).trim();
        return !revisionPart.isEmpty() && !filePart.isEmpty();
    }

    private boolean isValidFileTransferDownloadUrl(String url) {
        FileTransferRef ref = parseFileTransferFromUrl(url);
        return ref != null && isValidFileTransferRef(ref);
    }

    private boolean isInvalidFileTransferIdError(byte[] content) {
        if (content == null || content.length == 0) {
            return false;
        }
        String text = new String(content, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return text.contains("hex-представлении") || text.contains("не является числом в hex");
    }

    private boolean isPdfContent(byte[] content) {
        return content != null
                && content.length > 4
                && content[0] == '%'
                && content[1] == 'P'
                && content[2] == 'D'
                && content[3] == 'F';
    }

    private Map<String, Object> signAndSendToCounterparty(String sessionId, String documentId) {
        String certType = sabyAppProperties.getDeferredCertType();

        Map<String, Object> executeOnly = executeDeferredAction(sessionId, documentId, certType, null, null);
        if (Boolean.TRUE.equals(executeOnly.get("success"))) {
            return executeOnly;
        }

        Map<String, Object> prepareParams = buildPrepareParams(documentId, certType);
        Map<String, Object> prepareResult = invokeRpc(sessionId, "СБИС.ПодготовитьДействие", prepareParams, 2);
        if (!Boolean.TRUE.equals(prepareResult.get("success"))) {
            return prepareResult;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> preparedDoc = (Map<String, Object>) prepareResult.get("result");
        String stageId = extractStageId(preparedDoc);
        String actionName = extractActionName(preparedDoc);
        String revisionId = extractRevisionId(preparedDoc);

        return executeDeferredAction(sessionId, documentId, certType, stageId, actionName, revisionId);
    }

    private Map<String, Object> buildPrepareParams(String documentId, String certType) {
        Map<String, Object> cert = Map.of("Ключ", Map.of("Тип", certType));
        Map<String, Object> action = Map.of("Сертификат", cert);
        Map<String, Object> stage = Map.of("Действие", action);
        Map<String, Object> document = new HashMap<>();
        document.put("Идентификатор", documentId);
        document.put("Этап", stage);
        return Map.of("Документ", document);
    }

    private Map<String, Object> executeDeferredAction(
            String sessionId,
            String documentId,
            String certType,
            String stageId,
            String actionName
    ) {
        return executeDeferredAction(sessionId, documentId, certType, stageId, actionName, null);
    }

    private Map<String, Object> executeDeferredAction(
            String sessionId,
            String documentId,
            String certType,
            String stageId,
            String actionName,
            String revisionId
    ) {
        Map<String, Object> cert = Map.of("Ключ", Map.of("Тип", certType));
        Map<String, Object> action = new HashMap<>();
        action.put("Сертификат", cert);
        if (actionName != null && !actionName.isBlank()) {
            action.put("Название", actionName);
        }

        Map<String, Object> stage = new HashMap<>();
        stage.put("Действие", List.of(action));
        if (stageId != null && !stageId.isBlank()) {
            stage.put("Идентификатор", stageId);
        }

        Map<String, Object> document = new HashMap<>();
        document.put("Идентификатор", documentId);
        document.put("Этап", stage);
        if (revisionId != null && !revisionId.isBlank()) {
            document.put("Редакция", Map.of("Идентификатор", revisionId));
        }

        Map<String, Object> rpcResult = invokeRpc(sessionId, "СБИС.ВыполнитьДействие", Map.of("Документ", document), 4);
        if (!Boolean.TRUE.equals(rpcResult.get("success"))) {
            return rpcResult;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> resultDoc = (Map<String, Object>) rpcResult.get("result");
        Map<String, Object> success = new HashMap<>();
        success.put("success", true);
        success.put("status", extractStatus(resultDoc));
        success.put("message", "Подписано и отправлено контрагенту");
        return success;
    }

    private Map<String, Object> invokeRpc(String sessionId, String method, Map<String, Object> params, int id) {
        Map<String, Object> rpc = new HashMap<>();
        rpc.put("jsonrpc", "2.0");
        rpc.put("method", method);
        rpc.put("params", params);
        rpc.put("id", id);

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(rpc);
        } catch (JsonProcessingException e) {
            return buildError("Ошибка сериализации: " + e.getMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/json-rpc;charset=utf-8"));
        headers.set("X-SBISSessionId", sessionId);
        headers.set("User-Agent", "EDO-Control/1.0");
        headers.setAccept(List.of(MediaType.parseMediaType("application/json-rpc")));

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    DOCUMENT_SERVICE_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );
            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);
            if (responseBody != null && responseBody.containsKey("result")) {
                Object result = responseBody.get("result");
                if (result instanceof Map<?, ?> resultMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> doc = (Map<String, Object>) ((Map<?, ?>) result).get("Документ");
                    if (doc != null) {
                        Map<String, Object> ok = new HashMap<>();
                        ok.put("success", true);
                        ok.put("result", doc);
                        return ok;
                    }
                    Map<String, Object> ok = new HashMap<>();
                    ok.put("success", true);
                    ok.put("result", resultMap);
                    return ok;
                }
                Map<String, Object> ok = new HashMap<>();
                ok.put("success", true);
                ok.put("result", result);
                return ok;
            }
            if (responseBody != null && responseBody.containsKey("error")) {
                return buildError("СБИС (" + method + "): " + formatRpcError(responseBody.get("error")));
            }
            return buildError("Неожиданный ответ СБИС на " + method);
        } catch (HttpClientErrorException e) {
            return handleHttpError(e);
        } catch (ResourceAccessException e) {
            return buildError("Ошибка соединения с СБИС: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } catch (Exception e) {
            return buildError("Ошибка " + method + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private String formatRpcError(Object error) {
        if (error instanceof Map<?, ?> errMap) {
            Object msg = errMap.get("message");
            if (msg != null) {
                return String.valueOf(msg);
            }
            Object details = errMap.get("details");
            if (details != null) {
                return String.valueOf(details);
            }
        }
        return String.valueOf(error);
    }

    private boolean hasCounterpartyCompletedStage(Map<String, Object> document) {
        for (Object stageObj : asList(document.get("Этап"))) {
            if (!(stageObj instanceof Map<?, ?> stage)) {
                continue;
            }
            String stageName = String.valueOf(stage.get("Название"));
            if (stageName != null && stageName.toLowerCase(Locale.ROOT).contains("контрагент")) {
                for (Object actionObj : asList(stage.get("Действие"))) {
                    if (actionObj instanceof Map<?, ?> action) {
                        String actionName = String.valueOf(action.get("Название"));
                        if (actionName != null && actionName.toLowerCase(Locale.ROOT).contains("подпис")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private String extractStageId(Map<String, Object> document) {
        for (Object stageObj : asList(document.get("Этап"))) {
            if (stageObj instanceof Map<?, ?> stage && stage.get("Идентификатор") != null) {
                return String.valueOf(stage.get("Идентификатор"));
            }
        }
        return null;
    }

    private String extractActionName(Map<String, Object> document) {
        for (Object stageObj : asList(document.get("Этап"))) {
            if (!(stageObj instanceof Map<?, ?> stage)) {
                continue;
            }
            for (Object actionObj : asList(stage.get("Действие"))) {
                if (actionObj instanceof Map<?, ?> action && action.get("Название") != null) {
                    return String.valueOf(action.get("Название"));
                }
            }
        }
        return null;
    }

    private String extractRevisionId(Map<String, Object> document) {
        List<String> ids = collectRevisionIds(document);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private byte[] extractFileBytes(String sessionId, Map<String, Object> file) {
        Object binary = file.get("ДвоичныеДанные");
        if (binary != null && !String.valueOf(binary).isBlank()) {
            try {
                return Base64.getDecoder().decode(String.valueOf(binary));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        Object link = file.get("Ссылка");
        if (link == null || String.valueOf(link).isBlank()) {
            return null;
        }
        return downloadByLink(sessionId, String.valueOf(link));
    }

    private HttpHeaders buildDownloadHeaders(String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-SBISSessionID", sessionId);
        headers.set("X-SBISSessionId", sessionId);
        headers.set("User-Agent", "EDO-Control/1.0");
        headers.setAccept(List.of(
                MediaType.APPLICATION_PDF,
                MediaType.parseMediaType("application/octet-stream"),
                MediaType.ALL
        ));
        return headers;
    }

    private byte[] downloadByLink(String sessionId, String url) {
        String normalizedUrl = normalizeSbisDownloadUrl(url);
        if (normalizedUrl == null || normalizedUrl.isBlank()) {
            return null;
        }
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    URI.create(normalizedUrl),
                    HttpMethod.GET,
                    new HttpEntity<>(buildDownloadHeaders(sessionId)),
                    byte[].class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.debug("СБИС GET {}: {} {}", abbreviateUrl(normalizedUrl), e.getStatusCode().value(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("СБИС GET {}: {}", abbreviateUrl(normalizedUrl), e.getMessage());
            return null;
        }
    }

    private List<Object> asList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of(value);
    }

    private String guessContentType(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".xml")) {
            return "application/xml";
        }
        if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        return "application/octet-stream";
    }

    private Map<String, Object> mockSignAndSendSuccess(ActDocument act, String accountId) {
        Map<String, Object> mockResp = new HashMap<>();
        mockResp.put("success", true);
        mockResp.put("document_id", "SABY-MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        mockResp.put("mode", "mock");
        mockResp.put("message", "Акт подписан и отправлен контрагенту (имитация)");
        mockResp.put("act_number", act.getActNumber());
        mockResp.put("signed", true);
        mockResp.put("sent_to_counterparty", true);
        mockResp.put("account_id", accountId);
        mockResp.put("timestamp", Instant.now().toString());
        return mockResp;
    }

    private Map<String, Object> mockReadDocument(String documentId) {
        Map<String, Object> state = Map.of(
                "Название", "Ожидает подписания контрагентом",
                "Код", "3"
        );
        Map<String, Object> document = new HashMap<>();
        document.put("Идентификатор", documentId);
        document.put("Состояние", state);
        document.put("Вложение", List.of());

        Map<String, Object> success = new HashMap<>();
        success.put("success", true);
        success.put("document", document);
        success.put("status", state.get("Название"));
        return success;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;

        String date = dateStr.substring(0, 10); // обрезаем время если есть

        try {
            // Пробуем русский формат
            return LocalDate.parse(date, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (DateTimeParseException e1) {
            try {
                // Пробуем ISO формат
                return LocalDate.parse(date);
            } catch (DateTimeParseException e2) {
                // Если не удалось — логируем и возвращаем null
                System.err.println("Не удалось распарсить дату: " + dateStr);
                return null;
            }
        }
    }


    private Map<String, Object> buildOrganization(String inn, String kpp, String name) {
        if (inn == null || inn.isBlank()) {
            return Map.of();
        }

        // ИП или физлицо: 12 знаков ИНН
        if (inn.length() == 12) {
            Map<String, String> svfl = new HashMap<>();
            svfl.put("ИНН", inn);
            svfl.put("КодСтраны", "643");

            // Если name содержит ФИО — попробуем распарсить
            if (name != null && !name.isBlank() && name.contains(" ")) {
                String[] parts = name.trim().split("\\s+", 3);
                if (parts.length >= 1) svfl.put("Фамилия", parts[0]);
                if (parts.length >= 2) svfl.put("Имя", parts[1]);
                if (parts.length >= 3) svfl.put("Отчество", parts[2]);
            } else if (name != null && !name.isBlank()) {
                // Если не распарсилось — кладём всё в Фамилию
                svfl.put("Фамилия", name);
            }

            return Map.of("СвФЛ", svfl);
        }
        // Юрлицо: 10 знаков ИНН
        else if (inn.length() == 10) {
            Map<String, String> svul = new HashMap<>();
            svul.put("ИНН", inn);
            if (kpp != null && !kpp.isBlank()) svul.put("КПП", kpp);
            if (name != null && !name.isBlank()) svul.put("Название", name);
            svul.put("КодСтраны", "643");
            return Map.of("СвЮЛ", svul);
        }

        // Неизвестный формат ИНН
        return Map.of();
    }
}