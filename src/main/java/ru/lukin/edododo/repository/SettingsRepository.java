package ru.lukin.edododo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.lukin.edododo.model.SabySettingsDocument;

import java.util.Optional;

public interface SettingsRepository extends MongoRepository<SabySettingsDocument, String> {
    Optional<SabySettingsDocument> findByKey(String key);
}