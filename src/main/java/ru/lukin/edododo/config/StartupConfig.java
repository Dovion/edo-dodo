package ru.lukin.edododo.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.lukin.edododo.service.StorageService;

@Component
public class StartupConfig implements CommandLineRunner {

    private final StorageService storageService;

    public StartupConfig(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public void run(String... args) {
        try {
            storageService.initStorage();
        } catch (Exception ignored) {
        }
    }
}