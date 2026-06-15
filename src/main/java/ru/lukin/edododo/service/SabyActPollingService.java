package ru.lukin.edododo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.lukin.edododo.config.SabyAppProperties;
import ru.lukin.edododo.model.ActDocument;
import ru.lukin.edododo.model.ActStatus;
import ru.lukin.edododo.model.HistoryEntry;
import ru.lukin.edododo.repository.ActRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class SabyActPollingService {

    private static final Logger log = LoggerFactory.getLogger(SabyActPollingService.class);

    private final ActRepository actRepository;
    private final SabyService sabyService;
    private final SabyAppProperties sabyAppProperties;

    public SabyActPollingService(ActRepository actRepository, SabyService sabyService, SabyAppProperties sabyAppProperties) {
        this.actRepository = actRepository;
        this.sabyService = sabyService;
        this.sabyAppProperties = sabyAppProperties;
    }

    public void pollAwaitingCounterpartyActs() {
        if (!sabyAppProperties.isPollEnabled()) {
            return;
        }

        String awaitingStatus = ActStatus.ОТПРАВЛЕНО_КОНТРАГЕНТУ.getDisplayName();
        List<ActDocument> acts = actRepository.findAwaitingCounterpartyByStatus(awaitingStatus);
        if (acts.isEmpty()) {
            return;
        }

        log.info("Опрос СБИС: {} актов в статусе «{}»", acts.size(), awaitingStatus);
        Instant now = Instant.now();

        for (ActDocument act : acts) {
            try {
                processAct(act, now);
            } catch (Exception e) {
                log.warn("Ошибка опроса СБИС для акта {}: {}", act.getId(), e.getMessage());
            }
        }
    }

    private void processAct(ActDocument act, Instant now) {
        String docId = act.getSabySendId();
        if (docId == null || docId.isBlank()) {
            return;
        }

        Map<String, Object> readResult = sabyService.readDocument(docId, act.getSabyAccountId());
        if (!Boolean.TRUE.equals(readResult.get("success"))) {
            log.debug("Не удалось прочитать документ СБИС {}: {}", docId, readResult.get("message"));
            checkDeadline(act, now);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> document = (Map<String, Object>) readResult.get("document");

        boolean pdfUpdated = sabyService.syncStampedPdf(act, document, act.getSabyAccountId());
        if (pdfUpdated) {
            act.setUpdatedAt(now);
            actRepository.save(act);
            log.debug("Акт {}: PDF обновлён из СБИС (штамп подписи)", act.getId());
        }

        if (sabyService.isCounterpartySigned(document)) {
            int saved = sabyService.syncNewAttachments(act, document, act.getSabyAccountId());
            transitionToSigned(act, saved, pdfUpdated);
            return;
        }

        checkDeadline(act, now);
    }

    private void checkDeadline(ActDocument act, Instant now) {
        Instant deadline = act.getCounterpartyResponseDeadline();
        if (deadline == null || !now.isAfter(deadline)) {
            return;
        }

        String oldStatus = act.getStatus();
        act.setStatus(ActStatus.НЕТ_ОТВЕТА.getDisplayName());
        act.setUpdatedAt(now);
        int waitDays = act.getCounterpartyResponseWaitDays() != null
                ? act.getCounterpartyResponseWaitDays()
                : sabyAppProperties.getCounterpartyResponseWaitDays();
        act.getHistory().add(new HistoryEntry(
                now,
                oldStatus,
                ActStatus.НЕТ_ОТВЕТА.getDisplayName(),
                "Истёк срок ожидания подписи контрагента (" + waitDays + " дн.)"
        ));
        actRepository.save(act);
        log.info("Акт {} переведён в «Нет ответа» (дедлайн {})", act.getId(), deadline);
    }

    private void transitionToSigned(ActDocument act, int attachmentsSaved, boolean pdfUpdated) {
        String oldStatus = act.getStatus();
        Instant now = Instant.now();
        act.setStatus(ActStatus.ПОЛУЧЕН_ПОДПИСАННЫЙ.getDisplayName());
        act.setUpdatedAt(now);

        String details = "Контрагент подписал документ в СБИС";
        if (pdfUpdated) {
            details += ". PDF со штампом подписи обновлён";
        }
        if (attachmentsSaved > 0) {
            details += ". Сохранено вложений: " + attachmentsSaved;
        }

        act.getHistory().add(new HistoryEntry(
                now,
                oldStatus,
                ActStatus.ПОЛУЧЕН_ПОДПИСАННЫЙ.getDisplayName(),
                details
        ));
        actRepository.save(act);
        log.info("Акт {}: получена подпись контрагента", act.getId());
    }
}
