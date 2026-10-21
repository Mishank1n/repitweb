package ru.repitbot.notify;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LessonEndRulesTest {

    private static final Instant START = Instant.parse("2026-10-22T15:00:00Z");
    private static final Instant END_60 = START.plus(Duration.ofMinutes(60));

    @Test
    void notBeforeEnd() {
        assertFalse(LessonEndRules.ended(START, 60, END_60.minus(Duration.ofMinutes(1))));
        assertFalse(LessonEndRules.ended(START, 60, START));
    }

    @Test
    void rightAtEndAndShortlyAfter() {
        assertTrue(LessonEndRules.ended(START, 60, END_60));
        assertTrue(LessonEndRules.ended(START, 60, END_60.plus(Duration.ofMinutes(3))));
    }

    @Test
    void sixHourWindow() {
        // Догоняем после сна ноутбука, но о давно закончившихся занятиях не пишем.
        assertTrue(LessonEndRules.ended(START, 60, END_60.plus(Duration.ofHours(6)).minusSeconds(1)));
        assertFalse(LessonEndRules.ended(START, 60, END_60.plus(Duration.ofHours(6))));
    }

    @Test
    void longestLessonFitsSelectionMargin() {
        Instant end = START.plus(LessonEndRules.MAX_DURATION);
        assertFalse(LessonEndRules.ended(START, 300, end.minusSeconds(1)));
        assertTrue(LessonEndRules.ended(START, 300, end));
        // Выборка в ReminderDispatcher берёт занятия, начавшиеся не раньше now − WINDOW − MAX_DURATION.
        Instant lastMoment = end.plus(LessonEndRules.WINDOW).minusSeconds(1);
        assertFalse(START.isBefore(lastMoment.minus(LessonEndRules.WINDOW).minus(LessonEndRules.MAX_DURATION)));
    }
}
