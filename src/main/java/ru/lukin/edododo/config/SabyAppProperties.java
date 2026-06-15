package ru.lukin.edododo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.saby")
public class SabyAppProperties {

    /**
     * Тип отложенного сертификата для СБИС.ВыполнитьДействие:
     * «Отложенный» или «ОтложенныйСПодтверждением».
     */
    private String deferredCertType = "Отложенный";

    /** Срок ожидания подписи контрагента (календарные дни). */
    private int counterpartyResponseWaitDays = 10;

    private boolean pollEnabled = true;

    private String pollCron = "0 0 * * * ?";

    public String getDeferredCertType() {
        return deferredCertType;
    }

    public void setDeferredCertType(String deferredCertType) {
        this.deferredCertType = deferredCertType;
    }

    public int getCounterpartyResponseWaitDays() {
        return counterpartyResponseWaitDays;
    }

    public void setCounterpartyResponseWaitDays(int counterpartyResponseWaitDays) {
        this.counterpartyResponseWaitDays = counterpartyResponseWaitDays;
    }

    public boolean isPollEnabled() {
        return pollEnabled;
    }

    public void setPollEnabled(boolean pollEnabled) {
        this.pollEnabled = pollEnabled;
    }

    public String getPollCron() {
        return pollCron;
    }

    public void setPollCron(String pollCron) {
        this.pollCron = pollCron;
    }
}
