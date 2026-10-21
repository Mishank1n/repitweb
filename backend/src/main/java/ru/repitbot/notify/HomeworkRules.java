package ru.repitbot.notify;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Какое напоминание о сроке ДЗ пора отправить. Чистая функция, без БД: удобно тестировать. */
public final class HomeworkRules {

    public enum Kind { DUE24, OVERDUE }

    static final Duration DAY = Duration.ofHours(24);
    /** О просрочке напоминаем, только если срок прошёл недавно: после долгого сна ноутбука старое не шлём. */
    static final Duration OVERDUE_WINDOW = Duration.ofDays(7);

    private HomeworkRules() {
    }

    /**
     * @param dueAt    срок сдачи
     * @param issuedAt когда ДЗ выдано
     * @param now      текущий момент
     */
    public static Optional<Kind> due(Instant dueAt, Instant issuedAt, Instant now) {
        if (!now.isBefore(dueAt)) {
            return now.isBefore(dueAt.plus(OVERDUE_WINDOW)) ? Optional.of(Kind.OVERDUE) : Optional.empty();
        }
        Instant dayBefore = dueAt.minus(DAY);
        // Если ДЗ выдали меньше чем за сутки до срока, ученик и так только что получил его со сроком.
        if (!now.isBefore(dayBefore) && issuedAt.isBefore(dayBefore)) {
            return Optional.of(Kind.DUE24);
        }
        return Optional.empty();
    }
}
