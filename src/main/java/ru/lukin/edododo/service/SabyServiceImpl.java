package ru.lukin.edododo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import ru.lukin.edododo.dto.SabyAuthRequest;
import ru.lukin.edododo.model.ActDocument;
import ru.lukin.edododo.model.AttachmentFile;
import ru.lukin.edododo.model.FileDocument;
import ru.lukin.edododo.model.SabySettingsDocument;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class SabyServiceImpl implements SabyService {

    private static final String DEFAULT_AUTH_URL = "https://online.sbis.ru/auth/service/";
    private static final String DOCUMENT_SERVICE_URL = "https://online.sbis.ru/service/?srv=1";
    private static final DateTimeFormatter RU_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SettingsServiceImpl settingsService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FileService fileService;

    public SabyServiceImpl(SettingsServiceImpl settingsService, RestTemplate restTemplate, ObjectMapper objectMapper, FileService fileService) {
        this.settingsService = settingsService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.fileService = fileService;
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
                settingsService.saveSabySession(sessionId, authRequest.getLogin());
                String masked = maskSessionId(sessionId);

                Map<String, Object> success = new HashMap<>();
                success.put("success", true);
                success.put("message", "Авторизация успешна. SessionID сохранён, режим переключён на Production.");
                success.put("session_id_preview", masked);
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
    public Map<String, Object> sendActToSaby(ActDocument act) {
        SabySettingsDocument settings = settingsService.getSabySettings();
        String sessionId = settings.getApiToken();

        if (sessionId == null || sessionId.isBlank()) {
            return buildError("SessionID не установлен. Сначала выполните авторизацию.");
        }

        String mode = settings.getMode();

        // Mock-режим для тестов
        if ("mock".equals(mode)) {
            return mockSendSuccess(act);
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

                Map<String, Object> success = new HashMap<>();
                success.put("success", true);
                success.put("document_id", docId);
                success.put("mode", "production");
                success.put("message", "Акт успешно записан в СБИС");
                success.put("act_number", act.getActNumber());
                success.put("saby_link", link);
                success.put("status", status);
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

    private Map<String, Object> buildError(String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("success", false);
        err.put("message", message != null ? message : "Unknown error");
        err.put("timestamp", Instant.now().toString());
        return err;
    }

    private Map<String, Object> mockSendSuccess(ActDocument act) {
        Map<String, Object> mockResp = new HashMap<>();
        mockResp.put("success", true);
        mockResp.put("document_id", "SABY-MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        mockResp.put("mode", "mock");
        mockResp.put("message", "Акт записан (имитация)");
        mockResp.put("act_number", act.getActNumber());
        mockResp.put("timestamp", Instant.now().toString());
        return mockResp;
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