package ru.lukin.edododo.model;

public enum ActStatus {
    ГОТОВ_К_ОТПРАВКЕ("Готов к отправке"),
    ОТПРАВЛЕН("Отправлен"),
    ПОДПИСАН("Подписан"),
    НЕТ_ОТВЕТА("Нет ответа"),
    КОРРЕКТИРОВКИ("Корректировки"),
    В_РАБОТЕ_БУХГАЛТЕРИИ("В работе бухгалтерии"),
    ЗАКРЫТ("Закрыт");

    private final String displayName;

    ActStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ActStatus fromDisplayName(String value) {
        for (ActStatus status : values()) {
            if (status.displayName.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid status: " + value);
    }
}