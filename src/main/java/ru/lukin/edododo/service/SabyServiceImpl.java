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
import ru.lukin.edododo.model.SabySettingsDocument;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SabyServiceImpl implements SabyService {

    private static final String DEFAULT_AUTH_URL = "https://online.sbis.ru/auth/service/";
    private static final String API_URL = "https://api.sbis.ru/vok/";
    private static final String DEMO_API_URL = "https://api.sbis.ru/vok-demo/";

    private final SettingsServiceImpl settingsService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SabyServiceImpl(SettingsServiceImpl settingsService, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ─────────────────────────────────────────────────────────────
    // 🔐 АВТОРИЗАЦИЯ (точь-в-точь как в Python)
    // ─────────────────────────────────────────────────────────────
    @Override
    public Map<String, Object> authenticate(SabyAuthRequest authRequest) {
        SabySettingsDocument currentSettings = settingsService.getSabySettings();

        // Определяем URL для аутентификации
        String authUrl = currentSettings.getApiUrl() != null ? currentSettings.getApiUrl() : DEFAULT_AUTH_URL;
        if (!authUrl.contains("/auth/service")) {
            authUrl = DEFAULT_AUTH_URL;
        }

        // Формируем параметры в стиле СБИС (русские ключи!)
        Map<String, Object> params = new HashMap<>();
        params.put("Логин", authRequest.getLogin());
        params.put("Пароль", authRequest.getPassword());
        if (authRequest.getAccountNumber() != null && !authRequest.getAccountNumber().isBlank()) {
            params.put("НомерАккаунта", authRequest.getAccountNumber());
        }

        // JSON-RPC 2.0 тело запроса
        Map<String, Object> payload = new HashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", "СБИС.Аутентифицировать");
        payload.put("params", Map.of("Параметр", params)); // ✅ Вложенная структура!
        payload.put("id", 0);

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return buildError("Ошибка формирования запроса: " + e.getMessage());
        }

        // Заголовки — как в Python
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/json-rpc;charset=utf-8"));
        headers.set("User-Agent", "EDO-Control/1.0");
        headers.setAccept(List.of(MediaType.parseMediaType("application/json-rpc")));

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    authUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            Map<String, Object> data = objectMapper.readValue(response.getBody(), Map.class);

            // ✅ Успех: result содержит sessionId напрямую (не объект!)
            if (data != null && data.containsKey("result") && data.get("result") != null) {
                String sessionId = String.valueOf(data.get("result"));

                // Сохраняем сессию в настройки (как в Python)
                settingsService.saveSabySession(sessionId, authRequest.getLogin());

                // Маскируем sessionId для ответа
                String masked = maskSessionId(sessionId);

                Map<String, Object> success = new HashMap<>();
                success.put("success", true);
                success.put("message", "Авторизация успешна. SessionID сохранён, режим переключён на Production.");
                success.put("session_id_preview", masked);
                success.put("timestamp", Instant.now().toString());
                return success;
            }
            // ❌ Ошибка от СБИС
            else if (data != null && data.containsKey("error")) {
                Object err = data.get("error");
                String msg = "Неизвестная ошибка";
                    msg = String.valueOf(err);

                return buildError("Ошибка СБИС: " + msg);
            }
            // ❌ Неожиданный ответ
            else {
                String preview = data != null ?
                        objectMapper.writeValueAsString(data).substring(0, Math.min(300, objectMapper.writeValueAsString(data).length())) :
                        "пустой ответ";
                return buildError("Неожиданный ответ СБИС: " + preview);
            }

        } catch (HttpClientErrorException e) {
            // 4xx ошибки
            return handleHttpError(e);
        } catch (ResourceAccessException e) {
            // Таймаут / нет подключения
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
    // 📤 ОТПРАВКА АКТА (использует сохранённую сессию)
    // ─────────────────────────────────────────────────────────────
    @Override
    public Map<String, Object> sendActToSaby(ActDocument act) {
        SabySettingsDocument settings = settingsService.getSabySettings();
        if (settings.getApiToken() == null || settings.getApiToken().isBlank()) {
            return buildError("sessionId не установлен. Сначала выполните авторизацию.");
        }
        String mode = settings.getMode();
        String apiUrl = "mock".equals(mode) ? DEMO_API_URL : API_URL;

        // Mock-режим
        if ("mock".equals(mode)) {
            Map<String, Object> mockResp = new HashMap<>();
            mockResp.put("success", true);
            mockResp.put("document_id", "SABY-MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
            mockResp.put("mode", "mock");
            mockResp.put("message", "Акт отправлен (имитация)");
            mockResp.put("act_number", act.getActNumber());
            mockResp.put("timestamp", Instant.now().toString());
            return mockResp;
        }

        // Production: JSON-RPC запрос
        String requestBody;
        try {
            Map<String, Object> rpcBody = new HashMap<>();
            rpcBody.put("jsonrpc", "2.0");
            rpcBody.put("method", "ЭДО.ОтправитьАктСверки");

            Map<String, Object> params = new HashMap<>();
            params.put("act_number", act.getActNumber());
            params.put("counterparty", act.getCounterparty());
            params.put("inn", act.getInn());
            params.put("kpp", act.getKpp());
            params.put("amount", act.getAmount());
            params.put("period", act.getPeriod());
            params.put("legal_entity", act.getLegalEntity());
            params.put("saby_requisites", act.getSabyRequisites());

            rpcBody.put("params", params);
            rpcBody.put("id", UUID.randomUUID().toString());
            requestBody = objectMapper.writeValueAsString(rpcBody);
        } catch (JsonProcessingException e) {
            return buildError("Ошибка сериализации: " + e.getMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/json-rpc;charset=utf-8"));
        headers.set("X-SBISSessionId", settings.getApiToken());
        headers.set("User-Agent", "EDO-Control/1.0");
        headers.setAccept(List.of(MediaType.parseMediaType("application/json-rpc")));

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, request, String.class);
            Map<String, Object> body = objectMapper.readValue(response.getBody(), Map.class);

            if (body != null && body.containsKey("result")) {
                Map<String, Object> result = (Map<String, Object>) body.get("result");
                Map<String, Object> success = new HashMap<>();
                success.put("success", true);
                success.put("document_id", result.get("document_id"));
                success.put("mode", "production");
                success.put("message", "Акт успешно отправлен");
                success.put("act_number", act.getActNumber());
                success.put("timestamp", Instant.now().toString());
                return success;
            } else if (body != null && body.containsKey("error")) {
                return buildError("СБИС: " + body.get("error"));
            }
            return buildError("Неожиданный ответ от СБИС");

        } catch (HttpClientErrorException e) {
            return handleHttpError(e);
        } catch (Exception e) {
            return buildError("Ошибка: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 🔧 ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ─────────────────────────────────────────────────────────────
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
}