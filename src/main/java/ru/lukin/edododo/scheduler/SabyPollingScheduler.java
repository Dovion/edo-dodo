package ru.lukin.edododo.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.lukin.edododo.service.SabyActPollingService;

@Component
public class SabyPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(SabyPollingScheduler.class);

    private final SabyActPollingService pollingService;

    public SabyPollingScheduler(SabyActPollingService pollingService) {
        this.pollingService = pollingService;
    }

    @Scheduled(cron = "${app.saby.poll.cron:0 0 * * * ?}")
    public void pollCounterpartySignatures() {
        log.debug("Запуск планового опроса статусов СБИС");
        pollingService.pollAwaitingCounterpartyActs();
    }
}
