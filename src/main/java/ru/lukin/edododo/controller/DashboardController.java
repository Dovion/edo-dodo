package ru.lukin.edododo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.lukin.edododo.service.ActService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin
public class DashboardController {

    private final ActService actService;

    public DashboardController(ActService actService) {
        this.actService = actService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats(
            @RequestParam(required = false) String period,
            @RequestParam(required = false, value = "legal_entity") String legalEntity,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(actService.getDashboardStats(period, legalEntity, search));
    }

    @GetMapping("/attention")
    public ResponseEntity<Map<String, Object>> getAttentionItems(  // ← Map!
                                                                   @RequestParam(required = false) String period,
                                                                   @RequestParam(required = false, value = "legal_entity") String legalEntity,
                                                                   @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(actService.getAttentionItems(period, legalEntity, search));
    }

    @GetMapping("/stages")
    public ResponseEntity<List<Map<String, Object>>> getProcessStages(
            @RequestParam(required = false) String period,
            @RequestParam(required = false, value = "legal_entity") String legalEntity,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(actService.getProcessStages(period, legalEntity, search));
    }
}