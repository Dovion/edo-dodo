package ru.lukin.edododo.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.lukin.edododo.dto.SabySendRequest;
import ru.lukin.edododo.dto.StatusUpdateRequest;
import ru.lukin.edododo.model.ActDocument;
import ru.lukin.edododo.service.ActService;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class ActController {

    private final ActService actService;

    public ActController(ActService actService) {
        this.actService = actService;
    }

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of("message", "ЭДО Контроль API");
    }

    @GetMapping("/acts")
    public ResponseEntity<Map<String, Object>> getActs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, value = "legal_entity") String legalEntity,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(actService.getActs(status, legalEntity, counterparty, period, search, page, limit));
    }

    @GetMapping("/acts/{actId}")
    public ResponseEntity<ActDocument> getAct(@PathVariable String actId) {
        return ResponseEntity.ok(actService.getActById(actId));
    }

    @PatchMapping("/acts/{actId}/status")
    public ResponseEntity<ActDocument> updateActStatus(
            @PathVariable String actId,
            @RequestBody StatusUpdateRequest request
    ) {
        return ResponseEntity.ok(actService.updateActStatus(actId, request));
    }

    @PostMapping("/acts/{actId}/send-saby")
    public ResponseEntity<Map<String, Object>> sendToSaby(
            @PathVariable String actId,
            @RequestBody(required = false) SabySendRequest request
    ) {
        String documentType = request != null && request.getDocumentType() != null
                ? request.getDocumentType()
                : "reconciliation_act";
        Integer waitDays = request != null ? request.getCounterpartyResponseWaitDays() : null;
        String sabyAccountId = request != null ? request.getSabyAccountId() : null;
        return ResponseEntity.ok(actService.sendToSaby(actId, documentType, waitDays, sabyAccountId));
    }

    @PostMapping("/acts/send-batch")
    public ResponseEntity<Map<String, Object>> sendBatchToSaby(
            @RequestBody(required = false) SabySendRequest request
    ) {
        Integer waitDays = request != null ? request.getCounterpartyResponseWaitDays() : null;
        String sabyAccountId = request != null ? request.getSabyAccountId() : null;
        return ResponseEntity.ok(actService.sendBatchToSaby(waitDays, sabyAccountId));
    }

    @PostMapping(value = "/acts/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadActs(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(actService.uploadActs(file));
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedTestData() {
        return ResponseEntity.ok(actService.seedTestData());
    }

    @GetMapping("/acts/export/json")
    public ResponseEntity<Map<String, Object>> exportActs(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(actService.exportActs(status));
    }

    @GetMapping(value = "/acts/samples", produces = "application/zip")
    public ResponseEntity<byte[]> downloadSampleFiles() {
        byte[] zipBytes = actService.generateSampleFilesZip();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sample_acts.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipBytes);
    }
}