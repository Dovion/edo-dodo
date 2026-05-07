package ru.lukin.edododo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.lukin.edododo.exception.ExternalServiceException;

import java.util.HashMap;
import java.util.Map;

@Service
public class StorageServiceImpl implements StorageService {

    private static final String STORAGE_URL = "https://integrations.emergentagent.com/objstore/api/v1/storage";

    @Value("${EMERGENT_LLM_KEY:}")
    private String emergentKey;

    private final RestTemplate restTemplate;
    private String storageKey;

    public StorageServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void initStorage() {
        if (storageKey != null && !storageKey.isBlank()) {
            return;
        }

        Map<String, String> body = new HashMap<>();
        body.put("emergent_key", emergentKey);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                STORAGE_URL + "/init",
                new HttpEntity<>(body),
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ExternalServiceException("Не удалось инициализировать object storage");
        }

        Object key = response.getBody().get("storage_key");
        if (key == null) {
            throw new ExternalServiceException("Object storage не вернул storage_key");
        }

        storageKey = String.valueOf(key);
    }

    @Override
    public Map<String, Object> putObject(String path, byte[] data, String contentType) {
        initStorage();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Storage-Key", storageKey);
        headers.setContentType(MediaType.parseMediaType(contentType));

        ResponseEntity<Map> response = restTemplate.exchange(
                STORAGE_URL + "/objects/" + path,
                HttpMethod.PUT,
                new HttpEntity<>(data, headers),
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ExternalServiceException("Ошибка загрузки файла в object storage");
        }

        return response.getBody();
    }

    @Override
    public byte[] getObjectData(String path) {
        initStorage();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Storage-Key", storageKey);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                STORAGE_URL + "/objects/" + path,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ExternalServiceException("Ошибка загрузки файла из object storage");
        }

        return response.getBody();
    }

    @Override
    public String getObjectContentType(String path) {
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}