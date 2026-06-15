package ru.lukin.edododo.dto;

public class SabySendRequest {
    private String documentType = "reconciliation_act";
    private Integer counterpartyResponseWaitDays;
    private String sabyAccountId;

    public SabySendRequest() {
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public Integer getCounterpartyResponseWaitDays() {
        return counterpartyResponseWaitDays;
    }

    public void setCounterpartyResponseWaitDays(Integer counterpartyResponseWaitDays) {
        this.counterpartyResponseWaitDays = counterpartyResponseWaitDays;
    }

    public String getSabyAccountId() {
        return sabyAccountId;
    }

    public void setSabyAccountId(String sabyAccountId) {
        this.sabyAccountId = sabyAccountId;
    }
}