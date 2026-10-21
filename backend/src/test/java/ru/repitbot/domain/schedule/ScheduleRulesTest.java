package ru.repitbot.domain.schedule;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleRulesTest {

    @Test
    void weeklyOccurrencesWithinHorizon() {
        LocalDate wednesday = LocalDate.of(2026, 10, 14);
        List<LocalDate> mondays = ScheduleService.occurrences(DayOfWeek.MONDAY, wednesday,
                wednesday.plusDays(ScheduleService.HORIZON_DAYS));

        assertEquals(List.of(
                LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 9)), mondays);
    }

    @Test
    void occurrencesIncludeStartDayItself() {
        LocalDate wednesday = LocalDate.of(2026, 10, 14);
        List<LocalDate> dates = ScheduleService.occurrences(DayOfWeek.WEDNESDAY, wednesday, wednesday.plusDays(8));
        assertEquals(List.of(wednesday, wednesday.plusWeeks(1)), dates);
    }

    @Test
    void emptyWhenRangeTooShort() {
        LocalDate wednesday = LocalDate.of(2026, 10, 14);
        assertTrue(ScheduleService.occurrences(DayOfWeek.MONDAY, wednesday, wednesday.plusDays(3)).isEmpty());
    }

    @Test
    void russianDayLabels() {
        LocalDate today = LocalDate.of(2026, 10, 14);
        assertEquals("сегодня", When.day(today, today));
        assertEquals("завтра", When.day(today.plusDays(1), today));
        assertTrue(When.day(LocalDate.of(2026, 10, 20), today).contains("20 октября"), When.day(LocalDate.of(2026, 10, 20), today));
        assertEquals("пн", When.dow(DayOfWeek.MONDAY));
    }

    @Test
    void compactDateRoundTrip() {
        LocalDate date = LocalDate.of(2026, 1, 5);
        assertEquals("20260105", When.compact(date));
        assertEquals(date, When.parseCompact("20260105"));
    }
}
