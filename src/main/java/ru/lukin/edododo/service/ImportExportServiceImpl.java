package ru.lukin.edododo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImportExportServiceImpl implements ImportExportService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Map<String, String>> parseJson(byte[] content) {
        try {
            String text = new String(content, StandardCharsets.UTF_8);
            Object parsed = objectMapper.readValue(text, Object.class);
            List<Map<String, String>> result = new ArrayList<>();

            if (parsed instanceof List<?> list) {
                for (Object item : list) result.add(objectMapper.convertValue(item, Map.class));
            } else if (parsed instanceof Map<?, ?> map && map.containsKey("acts")) {
                Object acts = map.get("acts");
                if (acts instanceof List<?> list) {
                    for (Object item : list) result.add(objectMapper.convertValue(item, Map.class));
                } else {
                    result.add(objectMapper.convertValue(parsed, Map.class));
                }
            } else {
                result.add(objectMapper.convertValue(parsed, Map.class));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка чтения JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, String>> parseCsv(byte[] content) {
        try {
            BufferedReader br = new BufferedReader(new StringReader(new String(content, StandardCharsets.UTF_8)));
            String headerLine = br.readLine();
            if (headerLine == null) return List.of();

            String[] headers = headerLine.split(",");
            List<Map<String, String>> rows = new ArrayList<>();

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",", -1);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(headers[i].trim(), values[i].trim());
                }
                rows.add(row);
            }
            return rows;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка чтения CSV: " + e.getMessage(), e);
        }
    }
}