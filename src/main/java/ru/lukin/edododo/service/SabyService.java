package ru.lukin.edododo.service;

import ru.lukin.edododo.dto.SabyAuthRequest;
import ru.lukin.edododo.model.ActDocument;

import java.util.Map;

public interface SabyService {
    Map<String, Object> authenticate(SabyAuthRequest authRequest);

    /**
     * Записать акт в СБИС, подписать с нашей стороны (отложенный сертификат) и отправить контрагенту.
     */
    Map<String, Object> sendActToSaby(ActDocument act, String accountId);

    Map<String, Object> readDocument(String documentId, String accountId);

    boolean isCounterpartySigned(Map<String, Object> document);

    int syncNewAttachments(ActDocument act, Map<String, Object> document, String accountId);

    /**
     * Скачать актуальный PDF со штампом подписи из СБИС и заменить предыдущий PDF акта.
     *
     * @return true, если PDF обновлён
     */
    boolean syncStampedPdf(ActDocument act, Map<String, Object> document, String accountId);
}