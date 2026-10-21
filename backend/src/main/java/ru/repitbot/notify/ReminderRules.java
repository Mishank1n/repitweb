package ru.repitbot.notify;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Какое напоминание о занятии пора отправить. Чистая функция, без БД: удобно тестировать. */
public final class ReminderRules {

    public enum Kind { H24, H1 }

    static final Duration DAY = Duration.ofHours(24);
    static final Duration HOUR = Duration.ofHours(1);

    private ReminderRules() {
    }

    /**
     * @param startsAt  начало занятия
     * @param timeSetAt когда время занятия было назначено (создание или перенос)
     * @param now       текущий момент
     */
    public static Optional<Kind> due(Instant startsAt, Instant timeSetAt, Instant now) {
        if (!now.isBefore(startsAt)) {
            return Optional.empty();
        }
        Instant hourBefore = startsAt.minus(HOUR);
        if (!now.isBefore(hourBefore)) {
            // Догоняем и после сна ноутбука, пока занятие не началось.
            return Optional.of(Kind.H1);
        }
        Instant dayBefore = startsAt.minus(DAY);
        // Если занятие назначили меньше чем за сутки, участникам уже пришло сообщение о новом занятии.
        if (!now.isBefore(dayBefore) && timeSetAt.isBefore(dayBefore)) {
            return Optional.of(Kind.H24);
        }
        return Optional.empty();
    }
}
