package ru.lukin.edododo.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.lukin.edododo.model.ActDocument;
import ru.lukin.edododo.service.ActService;
import ru.lukin.edododo.service.ExcelExportService;

import java.util.List;

@RestController
@RequestMapping("/api/acts")
@CrossOrigin
public class ActExportController {

    private final ActService actService;
    private final ExcelExportService excelExportService;

    public ActExportController(ActService actService, ExcelExportService excelExportService) {
        this.actService = actService;
        this.excelExportService = excelExportService;
    }

    @GetMapping("/export/xlsx")
    public ResponseEntity<byte[]> exportActsToExcel(@RequestParam(required = false) String status) {
        @SuppressWarnings("unchecked")
        List<ActDocument> acts = (List<ActDocument>) actService.exportActs(status).get("acts");
        byte[] file = excelExportService.exportActsToExcel(acts);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=acts.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }
}