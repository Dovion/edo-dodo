package ru.lukin.edododo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "acts")
public class ActDocument {

    @Id
    private String mongoId;

    private String id;
    private String actNumber;
    private String legalEntity;
    private String counterparty;
    private String inn;
    private String kpp;
    private String period;
    private String formationDate;
    private Double amount;
    private String filePath;
    private String responsibleAccountant;
    private String sabyRequisites;

    private String status;
    private String sabySendId;
    private String sabyResponse;

    private Instant createdAt;
    private Instant updatedAt;

    private List<HistoryEntry> history = new ArrayList<>();

    public ActDocument() {
    }

    public String getMongoId() {
        return mongoId;
    }

    public void setMongoId(String mongoId) {
        this.mongoId = mongoId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getActNumber() {
        return actNumber;
    }

    public void setActNumber(String actNumber) {
        this.actNumber = actNumber;
    }

    public String getLegalEntity() {
        return legalEntity;
    }

    public void setLegalEntity(String legalEntity) {
        this.legalEntity = legalEntity;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    public String getInn() {
        return inn;
    }

    public void setInn(String inn) {
        this.inn = inn;
    }

    public String getKpp() {
        return kpp;
    }

    public void setKpp(String kpp) {
        this.kpp = kpp;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getFormationDate() {
        return formationDate;
    }

    public void setFormationDate(String formationDate) {
        this.formationDate = formationDate;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getResponsibleAccountant() {
        return responsibleAccountant;
    }

    public void setResponsibleAccountant(String responsibleAccountant) {
        this.responsibleAccountant = responsibleAccountant;
    }

    public String getSabyRequisites() {
        return sabyRequisites;
    }

    public void setSabyRequisites(String sabyRequisites) {
        this.sabyRequisites = sabyRequisites;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSabySendId() {
        return sabySendId;
    }

    public void setSabySendId(String sabySendId) {
        this.sabySendId = sabySendId;
    }

    public String getSabyResponse() {
        return sabyResponse;
    }

    public void setSabyResponse(String sabyResponse) {
        this.sabyResponse = sabyResponse;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<HistoryEntry> getHistory() {
        return history;
    }

    public void setHistory(List<HistoryEntry> history) {
        this.history = history;
    }
}