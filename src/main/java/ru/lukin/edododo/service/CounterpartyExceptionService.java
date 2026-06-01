package ru.lukin.edododo.service;

import org.springframework.stereotype.Service;
import ru.lukin.edododo.exception.BadRequestException;
import ru.lukin.edododo.model.ActDocument;
import ru.lukin.edododo.model.CounterpartyExceptionDocument;
import ru.lukin.edododo.repository.CounterpartyExceptionRepository;

import java.time.Instant;

@Service
public class CounterpartyExceptionService {

    private final CounterpartyExceptionRepository repository;

    public CounterpartyExceptionService(CounterpartyExceptionRepository repository) {
        this.repository = repository;
    }

    public boolean isException(String name, String inn) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return repository.existsByNameAndInn(name.trim(), normalizeInn(inn));
    }

    public boolean isException(ActDocument act) {
        if (act == null) {
            return false;
        }
        return isException(act.getCounterparty(), resolveInn(act));
    }

    public void setException(String name, String inn, boolean exception) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Укажите наименование контрагента");
        }
        String nameNorm = name.trim();
        String innNorm = normalizeInn(inn);
        if (exception) {
            if (!repository.existsByNameAndInn(nameNorm, innNorm)) {
                CounterpartyExceptionDocument doc = new CounterpartyExceptionDocument();
                doc.setName(nameNorm);
                doc.setInn(innNorm);
                doc.setCreatedAt(Instant.now());
                repository.save(doc);
            }
        } else {
            repository.deleteByNameAndInn(nameNorm, innNorm);
        }
    }

    public static String resolveInn(ActDocument act) {
        if (act.getCounterpartyInn() != null && !act.getCounterpartyInn().isBlank()) {
            return act.getCounterpartyInn().trim();
        }
        if (act.getInn() != null && !act.getInn().isBlank()) {
            return act.getInn().trim();
        }
        return "";
    }

    private static String normalizeInn(String inn) {
        return inn == null ? "" : inn.trim();
    }
}
