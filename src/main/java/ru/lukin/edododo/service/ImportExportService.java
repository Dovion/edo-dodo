package ru.lukin.edododo.service;

import java.util.List;
import java.util.Map;

public interface ImportExportService {
    List<Map<String, String>> parseJson(byte[] content);
    List<Map<String, String>> parseCsv(byte[] content);
}