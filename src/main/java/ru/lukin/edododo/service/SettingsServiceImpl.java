package ru.lukin.edododo.service;

import org.springframework.stereotype.Service;
import ru.lukin.edododo.exception.BadRequestException;
import ru.lukin.edododo.exception.ResourceNotFoundException;
import ru.lukin.edododo.model.SabyAccount;
import ru.lukin.edododo.model.SabySettingsDocument;
import ru.lukin.edododo.repository.SettingsRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class SettingsServiceImpl implements SettingsService {

    private final SettingsRepository settingsRepository;

    public SettingsServiceImpl(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @Override
    public SabySettingsDocument getSabySettings() {
        SabySettingsDocument doc = settingsRepository.findByKey("saby").orElseGet(() -> {
            SabySettingsDocument created = new SabySettingsDocument();
            created.setKey("saby");
            created.setMode("mock");
            created.setApiUrl("https://online.sbis.ru/service");
            created.setApiToken("");
            created.setOrgInn("");
            created.setAccounts(new ArrayList<>());
            return settingsRepository.save(created);
        });
        migrateLegacySessionIfNeeded(doc);
        return doc;
    }

    @Override
    public SabySettingsDocument updateSabySettings(SabySettingsDocument incoming) {
        SabySettingsDocument current = getSabySettings();

        if (incoming.getMode() != null && (incoming.getMode().equals("mock") || incoming.getMode().equals("production"))) {
            current.setMode(incoming.getMode());
        }
        if (incoming.getApiUrl() != null) {
            current.setApiUrl(incoming.getApiUrl());
        }
        if (incoming.getApiToken() != null && !incoming.getApiToken().startsWith("••")) {
            current.setApiToken(incoming.getApiToken());
        }
        if (incoming.getOrgInn() != null) {
            current.setOrgInn(incoming.getOrgInn());
        }

        return settingsRepository.save(current);
    }

    @Override
    public SabySettingsDocument getMaskedSabySettings() {
        SabySettingsDocument settings = getSabySettings();
        if (settings.getApiToken() != null && !settings.getApiToken().isBlank()) {
            settings.setApiToken(maskToken(settings.getApiToken()));
        } else {
            settings.setApiToken("");
        }

        List<SabyAccount> maskedAccounts = new ArrayList<>();
        for (SabyAccount account : settings.getAccounts()) {
            maskedAccounts.add(maskAccount(account));
        }
        settings.setAccounts(maskedAccounts);
        return settings;
    }

    @Override
    public List<SabyAccount> getSabyAccounts() {
        return new ArrayList<>(getSabySettings().getAccounts());
    }

    @Override
    public Optional<SabyAccount> getSabyAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        return getSabySettings().getAccounts().stream()
                .filter(account -> accountId.equals(account.getId()))
                .findFirst();
    }

    @Override
    public SabyAccount saveSabyAccount(String sessionId, String login, String accountNumber) {
        SabySettingsDocument settings = getSabySettings();
        String normalizedLogin = login != null ? login.trim() : "";
        String normalizedAccountNumber = accountNumber != null ? accountNumber.trim() : "";

        SabyAccount existing = settings.getAccounts().stream()
                .filter(account -> sameAccountIdentity(account, normalizedLogin, normalizedAccountNumber))
                .findFirst()
                .orElse(null);

        SabyAccount account = existing != null ? existing : new SabyAccount();
        if (existing == null) {
            account.setId(UUID.randomUUID().toString());
            settings.getAccounts().add(account);
        }

        account.setLogin(normalizedLogin);
        account.setSessionToken(sessionId);
        account.setAuthAt(Instant.now());
        account.setAccountNumber(normalizedAccountNumber.isBlank() ? null : normalizedAccountNumber);
        account.setDisplayName(buildDisplayName(normalizedLogin, normalizedAccountNumber, settings.getOrgInn()));

        settings.setApiToken(sessionId);
        settings.setApiLogin(normalizedLogin);
        settings.setAuthAt(account.getAuthAt());
        settings.setMode("production");

        settingsRepository.save(settings);
        return account;
    }

    @Override
    public void removeSabyAccount(String accountId) {
        SabySettingsDocument settings = getSabySettings();
        boolean removed = settings.getAccounts().removeIf(account -> accountId.equals(account.getId()));
        if (!removed) {
            throw new ResourceNotFoundException("Учётная запись СБИС не найдена");
        }

        if (settings.getAccounts().isEmpty()) {
            settings.setApiToken("");
            settings.setApiLogin(null);
            settings.setAuthAt(null);
        } else {
            SabyAccount primary = settings.getAccounts().get(0);
            settings.setApiToken(primary.getSessionToken());
            settings.setApiLogin(primary.getLogin());
            settings.setAuthAt(primary.getAuthAt());
        }

        settingsRepository.save(settings);
    }

    public String getOurCompanyInn() {
        SabySettingsDocument s = getSabySettings();
        return s != null ? s.getOurCompanyInn() : null;
    }

    public String getOurCompanyKpp() {
        SabySettingsDocument s = getSabySettings();
        return s != null ? s.getOurCompanyKpp() : null;
    }

    public String getOurCompanyName() {
        SabySettingsDocument s = getSabySettings();
        return s != null ? s.getOurCompanyName() : null;
    }

    public SabyAccount requireSabyAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            List<SabyAccount> accounts = getSabyAccounts();
            if (accounts.size() == 1) {
                return accounts.get(0);
            }
            throw new BadRequestException("Выберите учётную запись СБИС для отправки");
        }

        return getSabyAccount(accountId)
                .orElseThrow(() -> new BadRequestException("Учётная запись СБИС не найдена"));
    }

    private void migrateLegacySessionIfNeeded(SabySettingsDocument settings) {
        if (settings.getAccounts() == null) {
            settings.setAccounts(new ArrayList<>());
        }
        if (!settings.getAccounts().isEmpty()) {
            return;
        }

        String legacyToken = settings.getApiToken();
        if (legacyToken == null || legacyToken.isBlank()) {
            return;
        }

        SabyAccount account = new SabyAccount();
        account.setId(UUID.randomUUID().toString());
        account.setLogin(settings.getApiLogin());
        account.setSessionToken(legacyToken);
        account.setAuthAt(settings.getAuthAt());
        account.setOrgInn(settings.getOrgInn());
        account.setDisplayName(buildDisplayName(settings.getApiLogin(), null, settings.getOrgInn()));
        settings.getAccounts().add(account);
        settingsRepository.save(settings);
    }

    private boolean sameAccountIdentity(SabyAccount account, String login, String accountNumber) {
        if (!Objects.equals(normalize(account.getLogin()), login)) {
            return false;
        }
        return Objects.equals(normalize(account.getAccountNumber()), accountNumber);
    }

    private String normalize(String value) {
        return value != null ? value.trim() : "";
    }

    private String buildDisplayName(String login, String accountNumber, String orgInn) {
        if (accountNumber != null && !accountNumber.isBlank()) {
            return login + " (кабинет " + accountNumber + ")";
        }
        if (orgInn != null && !orgInn.isBlank()) {
            return login + " (ИНН " + orgInn + ")";
        }
        return login;
    }

    private SabyAccount maskAccount(SabyAccount source) {
        SabyAccount masked = new SabyAccount();
        masked.setId(source.getId());
        masked.setLogin(source.getLogin());
        masked.setAuthAt(source.getAuthAt());
        masked.setAccountNumber(source.getAccountNumber());
        masked.setDisplayName(source.getDisplayName());
        masked.setOrgInn(source.getOrgInn());
        masked.setOrgKpp(source.getOrgKpp());

        String token = source.getSessionToken();
        masked.setTokenSet(token != null && !token.isBlank());
        masked.setSessionToken(token != null && !token.isBlank() ? maskToken(token) : "");
        return masked;
    }

    private String maskToken(String token) {
        return token.length() > 4 ? "••••••••" + token.substring(token.length() - 4) : "••••";
    }
}
