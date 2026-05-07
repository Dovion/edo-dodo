package ru.lukin.edododo.service;

import org.springframework.web.multipart.MultipartFile;
import ru.lukin.edododo.model.FileDocument;

import java.util.List;

public interface FileService {
    FileDocument uploadFileToAct(String actId, MultipartFile file);
    List<FileDocument> getActFiles(String actId);
    byte[] downloadFile(String fileId);
    FileDocument getFileRecord(String fileId);
    void deleteFile(String fileId);
}