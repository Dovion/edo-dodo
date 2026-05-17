package ru.lukin.edododo.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AttachmentFile {
    private String originalName;  // "act_0079.xml"
    private byte[] content;       // байты файла
    private String contentType;   // "application/xml" или "application/pdf"
}