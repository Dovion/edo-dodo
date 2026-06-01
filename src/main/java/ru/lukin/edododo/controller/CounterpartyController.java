package ru.lukin.edododo.controller;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.lukin.edododo.dto.CounterpartyExceptionRequest;
import ru.lukin.edododo.service.CounterpartyExceptionService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.Fields.fields;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class CounterpartyController {

    private final MongoTemplate mongoTemplate;
    private final CounterpartyExceptionService counterpartyExceptionService;

    public CounterpartyController(
            MongoTemplate mongoTemplate,
            CounterpartyExceptionService counterpartyExceptionService
    ) {
        this.mongoTemplate = mongoTemplate;
        this.counterpartyExceptionService = counterpartyExceptionService;
    }

    @GetMapping("/counterparties")
    public List<Map<String, Object>> getCounterparties() {
        GroupOperation group = group(fields("counterparty", "inn"))
                .count().as("total_acts")
                .sum("amount").as("total_amount")
                .sum(ConditionalOperators.when(org.springframework.data.mongodb.core.query.Criteria.where("status").is("Закрыт")).then(1).otherwise(0)).as("closed")
                .sum(ConditionalOperators.when(org.springframework.data.mongodb.core.query.Criteria.where("status").is("Получен подписанный")).then(1).otherwise(0)).as("signed")
                .sum(ConditionalOperators.when(org.springframework.data.mongodb.core.query.Criteria.where("status").in(
                        "Загружено", "Отправлено в СБИС", "Отправлено Контрагенту", "Нет ответа"
                )).then(1).otherwise(0)).as("pending");

        Aggregation aggregation = newAggregation(
                group,
                project("total_acts", "total_amount", "closed", "signed", "pending")
                        .and("_id.counterparty").as("name")
                        .and("_id.inn").as("inn")
                        .andExclude("_id"),
                sort(Sort.Direction.DESC, "total_amount")
        );

        return mongoTemplate.aggregate(aggregation, "acts", Map.class).getMappedResults().stream()
                .map(row -> {
                    Map<String, Object> cp = new LinkedHashMap<>(row);
                    String name = String.valueOf(cp.get("name"));
                    String inn = cp.get("inn") == null ? "" : String.valueOf(cp.get("inn"));
                    cp.put("exception", counterpartyExceptionService.isException(name, inn));
                    return cp;
                })
                .toList();
    }

    @PatchMapping("/counterparties/exceptions")
    public ResponseEntity<Map<String, Object>> updateException(@RequestBody CounterpartyExceptionRequest request) {
        counterpartyExceptionService.setException(request.getName(), request.getInn(), request.isException());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", request.getName());
        body.put("inn", request.getInn() == null ? "" : request.getInn());
        body.put("exception", request.isException());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/legal-entities")
    public List<String> getLegalEntities() {
        Aggregation aggregation = newAggregation(
                group("legalEntity"),
                sort(Sort.Direction.ASC, "_id")
        );

        return mongoTemplate.aggregate(aggregation, "acts", Map.class).getMappedResults()
                .stream()
                .map(m -> String.valueOf(m.get("_id")))
                .filter(s -> !s.isBlank())
                .toList();
    }

    @GetMapping("/periods")
    public List<String> getPeriods() {
        Aggregation aggregation = newAggregation(
                group("period"),
                sort(Sort.Direction.DESC, "_id")
        );

        return mongoTemplate.aggregate(aggregation, "acts", Map.class).getMappedResults()
                .stream()
                .map(m -> String.valueOf(m.get("_id")))
                .filter(s -> !s.isBlank())
                .toList();
    }
}
