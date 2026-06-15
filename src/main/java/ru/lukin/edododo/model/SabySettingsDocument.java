package ru.lukin.edododo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "settings")
public class SabySettingsDocument {

    @Id
    private String mongoId;

    private String key;
    private String mode;
    private String apiUrl;
    private String apiToken;
    private String orgInn;
    private String apiLogin;
    private Instant authAt;
    private String ourCompanyInn;
    private String ourCompanyKpp;
    private String ourCompanyName;
    private List<SabyAccount> accounts = new ArrayList<>();


    public SabySettingsDocument() {
    }

    public String getMongoId() {
        return mongoId;
    }

    public void setMongoId(String mongoId) {
        this.mongoId = mongoId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getOrgInn() {
        return orgInn;
    }

    public void setOrgInn(String orgInn) {
        this.orgInn = orgInn;
    }

    public String getApiLogin() {
        return apiLogin;
    }

    public void setApiLogin(String apiLogin) {
        this.apiLogin = apiLogin;
    }

    public Instant getAuthAt() {
        return authAt;
    }

    public void setAuthAt(Instant authAt) {
        this.authAt = authAt;
    }

    public String getOurCompanyInn() {
        return ourCompanyInn;
    }

    public void setOurCompanyInn(String ourCompanyInn) {
        this.ourCompanyInn = ourCompanyInn;
    }

    public String getOurCompanyKpp() {
        return ourCompanyKpp;
    }

    public void setOurCompanyKpp(String ourCompanyKpp) {
        this.ourCompanyKpp = ourCompanyKpp;
    }

    public String getOurCompanyName() {
        return ourCompanyName;
    }

    public void setOurCompanyName(String ourCompanyName) {
        this.ourCompanyName = ourCompanyName;
    }

    public List<SabyAccount> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<SabyAccount> accounts) {
        this.accounts = accounts != null ? accounts : new ArrayList<>();
    }
}