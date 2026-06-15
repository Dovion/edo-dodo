package ru.lukin.edododo.service;

import org.springframework.web.multipart.MultipartFile;
import ru.lukin.edododo.dto.StatusUpdateRequest;
import ru.lukin.edododo.model.ActDocument;

import java.util.List;
import java.util.Map;

public interface ActService {
    Map<String, Object> getDashboardStats(String period, String legalEntity, String search);
    Map<String, Object> getAttentionItems(String period, String legalEntity, String search);
    List<Map<String, Object>> getProcessStages(String period, String legalEntity, String search);
    Map<String, Object> getActs(String status, String legalEntity, String counterparty, String period, String search, int page, int limit);
    Map<String, Object> exportActs(String status);
    ActDocument getActById(String actId);
    ActDocument updateActStatus(String actId, StatusUpdateRequest request);
    Map<String, Object> sendToSaby(String actId, String documentType, Integer counterpartyResponseWaitDays, String sabyAccountId);
    Map<String, Object> sendBatchToSaby(Integer counterpartyResponseWaitDays, String sabyAccountId);
    Map<String, Object> uploadActs(MultipartFile file);
    Map<String, Object> seedTestData();
    byte[] generateSampleFilesZip();

}