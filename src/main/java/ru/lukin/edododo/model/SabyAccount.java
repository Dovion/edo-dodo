package ru.lukin.edododo.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SabyAccount {

    private String id;
    private String login;
    private String sessionToken;
    private Instant authAt;
    private String accountNumber;
    private String displayName;
    private String orgInn;
    private String orgKpp;
    private Boolean tokenSet;

    public SabyAccount() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public Instant getAuthAt() {
        return authAt;
    }

    public void setAuthAt(Instant authAt) {
        this.authAt = authAt;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getOrgInn() {
        return orgInn;
    }

    public void setOrgInn(String orgInn) {
        this.orgInn = orgInn;
    }

    public String getOrgKpp() {
        return orgKpp;
    }

    public void setOrgKpp(String orgKpp) {
        this.orgKpp = orgKpp;
    }

    public Boolean getTokenSet() {
        return tokenSet;
    }

    public void setTokenSet(Boolean tokenSet) {
        this.tokenSet = tokenSet;
    }
}
