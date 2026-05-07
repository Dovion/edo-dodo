package ru.lukin.edododo.service;

public interface StorageClient {
    String initStorage();
    String upload(String path, byte[] data, String contentType);
    byte[] download(String path);
}