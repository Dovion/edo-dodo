package ru.lukin.edododo.dto;

public class SabySendRequest {
    private String documentType = "reconciliation_act";

    public SabySendRequest() {
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }
}