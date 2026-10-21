package ru.repitbot.notify;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.repitbot.notify.ReminderRules.Kind.H1;
import static ru.repitbot.notify.ReminderRules.Kind.H24;

class ReminderRulesTest {

    private static final Instant START = Instant.parse("2026-10-20T15:00:00Z");
    /** Занятие назначено за неделю: оба напоминания нужны. */
    private static final Instant SET_LONG_AGO = START.minus(Duration.ofDays(7));

    private static Optional<ReminderRules.Kind> due(Duration beforeStart, Instant timeSetAt) {
        return ReminderRules.due(START, timeSetAt, START.minus(beforeStart));
    }

    @Test
    void nothingBeforeDayWindow() {
        assertTrue(due(Duration.ofHours(25), SET_LONG_AGO).isEmpty());
    }

    @Test
    void dayReminderExactlyAt24Hours() {
        assertEquals(Optional.of(H24), due(Duration.ofHours(24), SET_LONG_AGO));
        assertEquals(Optional.of(H24), due(Duration.ofHours(2), SET_LONG_AGO));
    }

    @Test
    void hourReminderExactlyAtOneHour() {
        assertEquals(Optional.of(H1), due(Duration.ofHours(1), SET_LONG_AGO));
        assertEquals(Optional.of(H1), due(Duration.ofMinutes(1), SET_LONG_AGO));
    }

    @Test
    void catchUpAfterSleepSendsOnlyHourReminder() {
        // Ноутбук проспал момент «за 24 ч» и проснулся за 30 минут до занятия.
        assertEquals(Optional.of(H1), due(Duration.ofMinutes(30), SET_LONG_AGO));
    }

    @Test
    void nothingAfterStart() {
        assertTrue(ReminderRules.due(START, SET_LONG_AGO, START).isEmpty());
        assertTrue(ReminderRules.due(START, SET_LONG_AGO, START.plusSeconds(60)).isEmpty());
    }

    @Test
    void noDayReminderForLessonScheduledLessThanDayAhead() {
        // Занятие создано за 3 часа: участникам уже пришло «Новое занятие», напоминание за сутки лишнее.
        Instant setThreeHoursBefore = START.minus(Duration.ofHours(3));
        assertTrue(due(Duration.ofHours(2), setThreeHoursBefore).isEmpty());
        assertEquals(Optional.of(H1), due(Duration.ofMinutes(59), setThreeHoursBefore));
    }
}
