package ru.lukin.edododo.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.lukin.edododo.exception.ResourceNotFoundException;
import ru.lukin.edododo.model.ActDocument;
import ru.lukin.edododo.model.FileDocument;
import ru.lukin.edododo.repository.ActRepository;
import ru.lukin.edododo.repository.FileRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    private static final String UPLOAD_BASE = "uploads";

    private final FileRepository fileRepository;
    private final ActRepository actRepository;
    private final StorageService storageService;

    public FileServiceImpl(FileRepository fileRepository, ActRepository actRepository, StorageService storageService) {
        this.fileRepository = fileRepository;
        this.actRepository = actRepository;
        this.storageService = storageService;
    }

    @Override
    public FileDocument uploadFileToAct(String actId, MultipartFile file) {
        ActDocument act = actRepository.findById(actId)
                .orElseThrow(() -> new ResourceNotFoundException("Акт не найден"));

        try {
            Path uploadPath = Paths.get(UPLOAD_BASE, "acts", actId);
            Files.createDirectories(uploadPath);

            String filename = UUID.randomUUID() + getFileExtension(file.getOriginalFilename());
            Path filePath = uploadPath.resolve(filename);

            Files.write(filePath, file.getBytes());  // ✅ Сохраняется в ./uploads/

            FileDocument doc = new FileDocument();
            doc.setId(filename);
            doc.setActId(actId);
            doc.setStoragePath("acts/" + actId + "/" + filename);  // относительный путь
            doc.setOriginalFilename(file.getOriginalFilename());
            doc.setContentType(file.getContentType());
            doc.setSize(file.getSize());
            doc.setDeleted(false);
            doc.setCreatedAt(Instant.now());

            var res = fileRepository.save(doc);
            act.setFilePath(filePath.toString());  // абсолютный путь
            act.setUpdatedAt(Instant.now());
            actRepository.save(act);
            return res;
        } catch (IOException e) {
            throw new RuntimeException("Ошибка: " + e.getMessage(), e);
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    @Override
    public List<FileDocument> getActFiles(String actId) {
        return fileRepository.findByActIdAndDeletedFalse(actId);
    }

    @Override
    public byte[] downloadFile(String fileId) {
        FileDocument record = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Файл не найден"));

        Path filePath = Paths.get(UPLOAD_BASE, record.getStoragePath());

        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new ResourceNotFoundException("Файл не найден");
        }
    }

    @Override
    public FileDocument getFileRecord(String fileId) {
        return fileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Файл не найден"));
    }

    @Override
    public void deleteFile(String fileId) {
        FileDocument doc = getFileRecord(fileId);
        doc.setDeleted(true);
        fileRepository.save(doc);
    }

    @Override
    public boolean saveActFileIfNew(String actId, String originalFilename, byte[] content, String contentType) {
        if (originalFilename == null || originalFilename.isBlank() || content == null || content.length == 0) {
            return false;
        }

        boolean exists = fileRepository.findByActIdAndDeletedFalse(actId).stream()
                .anyMatch(f -> originalFilename.equalsIgnoreCase(f.getOriginalFilename()));
        if (exists) {
            return false;
        }

        actRepository.findById(actId)
                .orElseThrow(() -> new ResourceNotFoundException("Акт не найден"));

        try {
            Path uploadPath = Paths.get(UPLOAD_BASE, "acts", actId, "sbis");
            Files.createDirectories(uploadPath);

            String safeName = originalFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
            String storedName = UUID.randomUUID() + "_" + safeName;
            Path filePath = uploadPath.resolve(storedName);
            Files.write(filePath, content);

            FileDocument doc = new FileDocument();
            doc.setId(storedName);
            doc.setActId(actId);
            doc.setStoragePath("acts/" + actId + "/sbis/" + storedName);
            doc.setOriginalFilename(originalFilename);
            doc.setContentType(contentType != null ? contentType : "application/octet-stream");
            doc.setSize(content.length);
            doc.setDeleted(false);
            doc.setCreatedAt(Instant.now());
            fileRepository.save(doc);
            return true;
        } catch (IOException e) {
            throw new RuntimeException("Ошибка сохранения файла из СБИС: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean replaceActPdfFromSbis(String actId, byte[] content, String preferredFilename) {
        if (content == null || content.length == 0) {
            return false;
        }

        ActDocument act = actRepository.findById(actId)
                .orElseThrow(() -> new ResourceNotFoundException("Акт не найден"));

        for (FileDocument pdf : fileRepository.findByActIdAndDeletedFalse(actId)) {
            String name = pdf.getOriginalFilename();
            if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                pdf.setDeleted(true);
                fileRepository.save(pdf);
            }
        }

        try {
            Path uploadPath = Paths.get(UPLOAD_BASE, "acts", actId);
            Files.createDirectories(uploadPath);

            String displayName = buildStampedPdfFilename(act, preferredFilename);
            String storedName = UUID.randomUUID() + "_" + displayName.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path filePath = uploadPath.resolve(storedName);
            Files.write(filePath, content);

            FileDocument doc = new FileDocument();
            doc.setId(storedName);
            doc.setActId(actId);
            doc.setStoragePath("acts/" + actId + "/" + storedName);
            doc.setOriginalFilename(displayName);
            doc.setContentType("application/pdf");
            doc.setSize(content.length);
            doc.setDeleted(false);
            doc.setCreatedAt(Instant.now());
            fileRepository.save(doc);

            act.setFilePath(filePath.toString());
            act.setUpdatedAt(Instant.now());
            actRepository.save(act);
            return true;
        } catch (IOException e) {
            throw new RuntimeException("Ошибка замены PDF из СБИС: " + e.getMessage(), e);
        }
    }

    private String buildStampedPdfFilename(ActDocument act, String preferredFilename) {
        if (preferredFilename != null && !preferredFilename.isBlank()
                && preferredFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return preferredFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
        }
        String actNumber = act.getActNumber() != null ? act.getActNumber() : act.getId();
        return "Акт_" + actNumber + "_saby.pdf";
    }
}