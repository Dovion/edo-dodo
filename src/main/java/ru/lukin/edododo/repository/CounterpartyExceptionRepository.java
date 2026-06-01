package ru.lukin.edododo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.lukin.edododo.model.CounterpartyExceptionDocument;

public interface CounterpartyExceptionRepository extends MongoRepository<CounterpartyExceptionDocument, String> {
    boolean existsByNameAndInn(String name, String inn);

    void deleteByNameAndInn(String name, String inn);
}
