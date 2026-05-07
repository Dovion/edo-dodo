package ru.lukin.edododo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.lukin.edododo.dto.SabyAuthRequest;
import ru.lukin.edododo.model.SabySettingsDocument;
import ru.lukin.edododo.service.SabyService;
import ru.lukin.edododo.service.SettingsService;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin
public class SettingsController {

    private final SabyService sabyService;
    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService, SabyService sabyService) {
        this.settingsService = settingsService;
        this.sabyService = sabyService;
    }

    @GetMapping("/saby")
    public ResponseEntity<SabySettingsDocument> getSabySettings() {
        return ResponseEntity.ok(settingsService.getMaskedSabySettings());
    }

    @PutMapping("/saby")
    public ResponseEntity<SabySettingsDocument> updateSabySettings(@RequestBody SabySettingsDocument settings) {
        SabySettingsDocument updated = settingsService.updateSabySettings(settings);
        return ResponseEntity.ok(settingsService.getMaskedSabySettings());
    }

    @PostMapping("/saby/auth")
    public ResponseEntity<Map<String, Object>> sabyAuthenticate(@RequestBody SabyAuthRequest authRequest) {
        if (authRequest.getLogin() == null || authRequest.getLogin().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Логин обязателен"));
        }
        if (authRequest.getPassword() == null || authRequest.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Пароль обязателен"));
        }

        Map<String, Object> result = sabyService.authenticate(authRequest);

        HttpStatus status = Boolean.TRUE.equals(result.get("success"))
                ? HttpStatus.OK
                : HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(result);
    }
}