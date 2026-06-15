package ru.lukin.edododo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import ru.lukin.edododo.model.ActDocument;

import java.util.List;
import java.util.Optional;

public interface ActRepository extends MongoRepository<ActDocument, String> {
    @Query("{ 'id' : ?0 }")
    Optional<ActDocument> findById(String id);
    long countByStatus(String status);

    @Query("{ 'status': ?0, 'sabySendId': { $exists: true, $ne: null, $ne: '' } }")
    List<ActDocument> findAwaitingCounterpartyByStatus(String status);
}