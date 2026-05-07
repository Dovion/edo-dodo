package ru.lukin.edododo.service;

import ru.lukin.edododo.model.SabySettingsDocument;

public interface SettingsService {
    SabySettingsDocument getSabySettings();
    SabySettingsDocument updateSabySettings(SabySettingsDocument incoming);
    SabySettingsDocument getMaskedSabySettings();
}