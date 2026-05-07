package ru.lukin.edododo.service;

import ru.lukin.edododo.model.ActDocument;

import java.util.List;

public interface ExcelExportService {
    byte[] exportActsToExcel(List<ActDocument> acts);
}