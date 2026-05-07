package ru.lukin.edododo.service;

import java.util.Map;

public interface StorageService {
    void initStorage();
    Map<String, Object> putObject(String path, byte[] data, String contentType);
    byte[] getObjectData(String path);
    String getObjectContentType(String path);
}