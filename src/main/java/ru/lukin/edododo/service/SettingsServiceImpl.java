package ru.lukin.edododo.service;

import org.springframework.stereotype.Service;
import ru.lukin.edododo.model.SabySettingsDocument;
import ru.lukin.edododo.repository.SettingsRepository;

import java.time.Instant;

@Service
public class SettingsServiceImpl implements SettingsService {

    private final SettingsRepository settingsRepository;

    public SettingsServiceImpl(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @Override
    public SabySettingsDocument getSabySettings() {
        return settingsRepository.findByKey("saby").orElseGet(() -> {
            SabySettingsDocument doc = new SabySettingsDocument();
            doc.setKey("saby");
            doc.setMode("mock");
            doc.setApiUrl("https://online.sbis.ru/service");
            doc.setApiToken("");
            doc.setOrgInn("");
            return settingsRepository.save(doc);
        });
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
            String token = settings.getApiToken();
            settings.setApiToken(token.length() > 4 ? "••••••••" + token.substring(token.length() - 4) : "••••");
        } else {
            settings.setApiToken("");
        }
        return settings;
    }

    public void saveSabySession(String sessionId, String login) {
        SabySettingsDocument settings = getSabySettings();
        if (settings == null) {
            settings = new SabySettingsDocument();
            settings.setKey("saby");
        }

        settings.setApiToken(sessionId);        // sessionId сохраняется как api_token
        settings.setMode("production");         // переключаем режим
        settings.setApiLogin(login);            // сохраняем логин для возможных реавторизаций
        settings.setAuthAt(Instant.now());      // время последней авторизации

        settingsRepository.save(settings);
    }

}