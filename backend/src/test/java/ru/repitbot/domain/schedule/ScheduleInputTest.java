package ru.repitbot.domain.schedule;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScheduleInputTest {

    @Test
    void duration() {
        assertEquals(60, ScheduleInput.duration(60));
        assertEquals(15, ScheduleInput.duration(15));
        assertEquals(300, ScheduleInput.duration(300));
        assertThrows(IllegalArgumentException.class, () -> ScheduleInput.duration(10));
        assertThrows(IllegalArgumentException.class, () -> ScheduleInput.duration(301));
        assertThrows(IllegalArgumentException.class, () -> ScheduleInput.duration(null));
    }

    @Test
    void dayOfWeek() {
        assertEquals(DayOfWeek.MONDAY, ScheduleInput.day(1));
        assertEquals(DayOfWeek.SUNDAY, ScheduleInput.day(7));
        assertThrows(IllegalArgumentException.class, () -> ScheduleInput.day(0));
        assertThrows(IllegalArgumentException.class, () -> ScheduleInput.day(8));
    }

    @Test
    void time() {
        assertEquals(LocalTime.of(18, 0), ScheduleInput.time("18:00"));
        assertEquals(LocalTime.of(9, 30), ScheduleInput.time(" 9:30 "));
        assertThrows(IllegalArgumentException.class, () -> ScheduleInput.time("24:00"));
        assertThrows(IllegalArgumentException.class, () -> ScheduleInput.time("18:60"));
        assertThrows(IllegalArgumentException.class, () -> ScheduleInput.time("18"));
        assertThrows(IllegalArgumentException.class, () -> ScheduleInput.time(null));
    }

    @Test
    void date() {
        assertEquals(LocalDate.of(2026, 10, 25), ScheduleInput.date("2026-10-25"));
        assertThrows(IllegalArgumentException.class, () -> ScheduleInput.date("25.10.2026"));
        assertThrows(IllegalArgumentException.class, () -> ScheduleInput.date("2026-02-30"));
        assertThrows(IllegalArgumentException.class, () -> ScheduleInput.date(null));
    }
}
