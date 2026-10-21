package ru.repitbot.domain.schedule;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LessonTimesTest {

    private static final Instant START = Instant.parse("2026-10-17T13:00:00Z");

    private static Lesson lesson45() {
        return Lesson.oneOff(null, START, 45);
    }

    @Test
    void beforeStartIsUpcomingNotOngoing() {
        Instant now = START.minus(Duration.ofMinutes(1));
        assertTrue(lesson45().isUpcoming(now));
        assertFalse(lesson45().isOngoing(now));
        assertFalse(lesson45().hasEnded(now));
    }

    @Test
    void duringLessonIsOngoing() {
        // Скриншот пользователя: 16:13 МСК при занятии 16:00–16:45 — занятие должно быть видно как идущее.
        Instant now = START.plus(Duration.ofMinutes(13));
        assertFalse(lesson45().isUpcoming(now));
        assertTrue(lesson45().isOngoing(now));
        assertFalse(lesson45().hasEnded(now));
        assertTrue(lesson45().isOngoing(START));
    }

    @Test
    void endsExactlyAfterDuration() {
        Instant end = START.plus(Duration.ofMinutes(45));
        assertTrue(lesson45().isOngoing(end.minusSeconds(1)));
        assertFalse(lesson45().isOngoing(end));
        assertTrue(lesson45().hasEnded(end));
    }

    @Test
    void cancelledLessonIsNeverOngoing() {
        Lesson l = lesson45();
        l.cancel();
        assertFalse(l.isOngoing(START.plus(Duration.ofMinutes(10))));
    }
}
