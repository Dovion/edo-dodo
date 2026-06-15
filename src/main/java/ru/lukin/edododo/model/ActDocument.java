package ru.lukin.edododo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
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

    // === Контрагент (получатель акта) ===
    private String counterparty;              // ФИО или название организации
    private String counterpartyInn;           // ИНН контрагента (10 для юрлиц, 12 для ИП)
    private String counterpartyKpp;           // КПП контрагента (только для юрлиц, пусто для ИП)
    private String counterpartyType;          // "UL" (юрлицо), "IP" (ИП), "FL" (физлицо)

    // ФИО для ИП/физлиц (раздельно для удобной отправки в СБИС)
    private String counterpartyLastName;
    private String counterpartyFirstName;
    private String counterpartyPatronymic;

    // === Наша организация (отправитель) ===
    private String ourCompanyInn;
    private String ourCompanyKpp;
    private String ourCompanyName;

    // === Общие поля акта ===
    private String inn;                       // @Deprecated: используйте counterpartyInn / ourCompanyInn
    private String kpp;                       // @Deprecated: используйте counterpartyKpp / ourCompanyKpp
    private String period;
    private String formationDate;
    private Double amount;
    private String filePath;
    private String responsibleAccountant;
    private String sabyRequisites;

    private String status;
    private String sabySendId;
    private String sabyAccountId;
    private String sabyResponse;

    /** Момент отправки контрагенту (после нашей подписи). */
    private Instant sentToCounterpartyAt;
    /** Крайний срок ожидания подписи контрагента. */
    private Instant counterpartyResponseDeadline;
    /** Срок ожидания ответа контрагента (календарные дни), заданный при отправке. */
    private Integer counterpartyResponseWaitDays;

    private Instant createdAt;
    private Instant updatedAt;

    private List<HistoryEntry> history = new ArrayList<>();

    @Transient
    private Boolean counterpartyException;

    public ActDocument() {
    }

    // === Геттеры и сеттеры для mongoId и id ===
    public String getMongoId() { return mongoId; }
    public void setMongoId(String mongoId) { this.mongoId = mongoId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    // === Основные поля ===
    public String getActNumber() { return actNumber; }
    public void setActNumber(String actNumber) { this.actNumber = actNumber; }

    public String getLegalEntity() { return legalEntity; }
    public void setLegalEntity(String legalEntity) { this.legalEntity = legalEntity; }

    // === Контрагент ===
    public String getCounterparty() { return counterparty; }
    public void setCounterparty(String counterparty) { this.counterparty = counterparty; }

    public String getCounterpartyInn() { return counterpartyInn; }
    public void setCounterpartyInn(String counterpartyInn) { this.counterpartyInn = counterpartyInn; }

    public String getCounterpartyKpp() { return counterpartyKpp; }
    public void setCounterpartyKpp(String counterpartyKpp) { this.counterpartyKpp = counterpartyKpp; }

    public String getCounterpartyType() { return counterpartyType; }
    public void setCounterpartyType(String counterpartyType) { this.counterpartyType = counterpartyType; }

    public String getCounterpartyLastName() { return counterpartyLastName; }
    public void setCounterpartyLastName(String counterpartyLastName) { this.counterpartyLastName = counterpartyLastName; }

    public String getCounterpartyFirstName() { return counterpartyFirstName; }
    public void setCounterpartyFirstName(String counterpartyFirstName) { this.counterpartyFirstName = counterpartyFirstName; }

    public String getCounterpartyPatronymic() { return counterpartyPatronymic; }
    public void setCounterpartyPatronymic(String counterpartyPatronymic) { this.counterpartyPatronymic = counterpartyPatronymic; }

    // === Наша организация ===
    public String getOurCompanyInn() { return ourCompanyInn; }
    public void setOurCompanyInn(String ourCompanyInn) { this.ourCompanyInn = ourCompanyInn; }

    public String getOurCompanyKpp() { return ourCompanyKpp; }
    public void setOurCompanyKpp(String ourCompanyKpp) { this.ourCompanyKpp = ourCompanyKpp; }

    public String getOurCompanyName() { return ourCompanyName; }
    public void setOurCompanyName(String ourCompanyName) { this.ourCompanyName = ourCompanyName; }

    // === Устаревшие поля (для обратной совместимости) ===
    @Deprecated
    public String getInn() { return inn; }
    @Deprecated
    public void setInn(String inn) { this.inn = inn; }

    @Deprecated
    public String getKpp() { return kpp; }
    @Deprecated
    public void setKpp(String kpp) { this.kpp = kpp; }

    // === Остальные поля ===
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getFormationDate() { return formationDate; }
    public void setFormationDate(String formationDate) { this.formationDate = formationDate; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getResponsibleAccountant() { return responsibleAccountant; }
    public void setResponsibleAccountant(String responsibleAccountant) { this.responsibleAccountant = responsibleAccountant; }

    public String getSabyRequisites() { return sabyRequisites; }
    public void setSabyRequisites(String sabyRequisites) { this.sabyRequisites = sabyRequisites; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSabySendId() { return sabySendId; }
    public void setSabySendId(String sabySendId) { this.sabySendId = sabySendId; }

    public String getSabyAccountId() { return sabyAccountId; }
    public void setSabyAccountId(String sabyAccountId) { this.sabyAccountId = sabyAccountId; }

    public String getSabyResponse() { return sabyResponse; }
    public void setSabyResponse(String sabyResponse) { this.sabyResponse = sabyResponse; }

    public Instant getSentToCounterpartyAt() { return sentToCounterpartyAt; }
    public void setSentToCounterpartyAt(Instant sentToCounterpartyAt) { this.sentToCounterpartyAt = sentToCounterpartyAt; }

    public Instant getCounterpartyResponseDeadline() { return counterpartyResponseDeadline; }
    public void setCounterpartyResponseDeadline(Instant counterpartyResponseDeadline) {
        this.counterpartyResponseDeadline = counterpartyResponseDeadline;
    }

    public Integer getCounterpartyResponseWaitDays() { return counterpartyResponseWaitDays; }
    public void setCounterpartyResponseWaitDays(Integer counterpartyResponseWaitDays) {
        this.counterpartyResponseWaitDays = counterpartyResponseWaitDays;
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<HistoryEntry> getHistory() { return history; }
    public void setHistory(List<HistoryEntry> history) { this.history = history; }

    public Boolean getCounterpartyException() { return counterpartyException; }
    public void setCounterpartyException(Boolean counterpartyException) { this.counterpartyException = counterpartyException; }
}