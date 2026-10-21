package ru.repitbot.notify;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportRulesTest {

    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 18);
    private static final LocalTime SIX_PM = LocalTime.of(18, 0);

    @Test
    void reportDayIsTheDayItselfOrTheNextDay() {
        assertEquals(Optional.of(SUNDAY), ReportRules.reportDay(SUNDAY, DayOfWeek.SUNDAY));
        assertEquals(Optional.of(SUNDAY), ReportRules.reportDay(SUNDAY.plusDays(1), DayOfWeek.SUNDAY));
        assertTrue(ReportRules.reportDay(SUNDAY.plusDays(2), DayOfWeek.SUNDAY).isEmpty());
        assertTrue(ReportRules.reportDay(SUNDAY.minusDays(1), DayOfWeek.SUNDAY).isEmpty());
    }

    @Test
    void weekIsMondayToSunday() {
        assertEquals(LocalDate.of(2026, 10, 12), ReportRules.weekStart(SUNDAY));
        assertEquals(DayOfWeek.MONDAY, ReportRules.weekStart(SUNDAY).getDayOfWeek());
    }

    @Test
    void promptOneHourBefore() {
        assertFalse(ReportRules.promptDue(SUNDAY.atTime(16, 59), SUNDAY, SIX_PM));
        assertTrue(ReportRules.promptDue(SUNDAY.atTime(17, 0), SUNDAY, SIX_PM));
        // Догоняем в понедельник, но не во вторник.
        assertTrue(ReportRules.promptDue(SUNDAY.plusDays(1).atTime(23, 59), SUNDAY, SIX_PM));
        assertFalse(ReportRules.promptDue(SUNDAY.plusDays(2).atStartOfDay(), SUNDAY, SIX_PM));
    }

    @Test
    void sendAtScheduledTimeWhenPromptWasInTime() {
        LocalDateTime prompt = SUNDAY.atTime(17, 0);
        assertFalse(ReportRules.sendDue(SUNDAY.atTime(17, 59), SUNDAY, SIX_PM, prompt));
        assertTrue(ReportRules.sendDue(SUNDAY.atTime(18, 0), SUNDAY, SIX_PM, prompt));
    }

    @Test
    void afterSleepTutorStillGetsAnHour() {
        LocalDateTime prompt = SUNDAY.atTime(18, 30);
        assertEquals(SUNDAY.atTime(19, 30), ReportRules.sendAt(SUNDAY, SIX_PM, prompt));
        assertFalse(ReportRules.sendDue(SUNDAY.atTime(19, 29), SUNDAY, SIX_PM, prompt));
        assertTrue(ReportRules.sendDue(SUNDAY.atTime(19, 30), SUNDAY, SIX_PM, prompt));
    }

    @Test
    void catchUpOnMondayButNotLater() {
        LocalDateTime prompt = SUNDAY.plusDays(1).atTime(9, 0);
        assertTrue(ReportRules.sendDue(SUNDAY.plusDays(1).atTime(10, 0), SUNDAY, SIX_PM, prompt));
        assertFalse(ReportRules.sendDue(SUNDAY.plusDays(2).atTime(10, 0), SUNDAY, SIX_PM, prompt));
    }
}
