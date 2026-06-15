package ru.lukin.edododo.service;

import ru.lukin.edododo.model.SabyAccount;
import ru.lukin.edododo.model.SabySettingsDocument;

import java.util.List;
import java.util.Optional;

public interface SettingsService {
    SabySettingsDocument getSabySettings();
    SabySettingsDocument updateSabySettings(SabySettingsDocument incoming);
    SabySettingsDocument getMaskedSabySettings();
    List<SabyAccount> getSabyAccounts();
    Optional<SabyAccount> getSabyAccount(String accountId);
    SabyAccount saveSabyAccount(String sessionId, String login, String accountNumber);
    void removeSabyAccount(String accountId);
}
