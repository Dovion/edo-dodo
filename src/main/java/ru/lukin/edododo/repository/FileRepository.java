package ru.lukin.edododo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import ru.lukin.edododo.model.FileDocument;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends MongoRepository<FileDocument, String> {
    @Query("{ 'id' : ?0 }")
    Optional<FileDocument> findById(String id);
    List<FileDocument> findByActIdAndDeletedFalse(String actId);
    Optional<FileDocument> findByIdAndDeletedFalse(String id);
}