package ru.lukin.edododo.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.lukin.edododo.model.FileDocument;
import ru.lukin.edododo.service.FileService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/acts/{actId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileDocument> uploadFileToAct(
            @PathVariable String actId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(fileService.uploadFileToAct(actId, file));
    }

    @GetMapping("/acts/{actId}/files")
    public ResponseEntity<List<FileDocument>> getActFiles(@PathVariable String actId) {
        return ResponseEntity.ok(fileService.getActFiles(actId));
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileId) {
        FileDocument record = fileService.getFileRecord(fileId);
        byte[] data = fileService.downloadFile(fileId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(record.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + record.getOriginalFilename() + "\"")
                .body(data);
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Map<String, String>> deleteFile(@PathVariable String fileId) {
        fileService.deleteFile(fileId);
        return ResponseEntity.ok(Map.of("message", "Файл удалён"));
    }
}