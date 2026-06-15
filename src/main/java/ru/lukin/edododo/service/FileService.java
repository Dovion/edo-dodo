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

    /**
     * Сохранить файл, полученный из СБИС, если такого имени ещё нет у акта.
     *
     * @return true, если файл сохранён
     */
    boolean saveActFileIfNew(String actId, String originalFilename, byte[] content, String contentType);

    /**
     * Заменить все PDF-вложения акта на актуальный файл из СБИС (со штампом подписи).
     *
     * @return true, если файл сохранён
     */
    boolean replaceActPdfFromSbis(String actId, byte[] content, String preferredFilename);
}