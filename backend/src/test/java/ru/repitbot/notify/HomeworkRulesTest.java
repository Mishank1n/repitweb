package ru.repitbot.notify;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.repitbot.notify.HomeworkRules.Kind.DUE24;
import static ru.repitbot.notify.HomeworkRules.Kind.OVERDUE;

class HomeworkRulesTest {

    private static final Instant DUE = Instant.parse("2026-10-22T15:00:00Z");
    private static final Instant ISSUED_WEEK_BEFORE = DUE.minus(Duration.ofDays(7));

    private static Optional<HomeworkRules.Kind> at(Duration beforeDue, Instant issuedAt) {
        return HomeworkRules.due(DUE, issuedAt, DUE.minus(beforeDue));
    }

    @Test
    void nothingEarlierThanDayBefore() {
        assertTrue(at(Duration.ofHours(25), ISSUED_WEEK_BEFORE).isEmpty());
    }

    @Test
    void dayReminderFromExactly24Hours() {
        assertEquals(Optional.of(DUE24), at(Duration.ofHours(24), ISSUED_WEEK_BEFORE));
        assertEquals(Optional.of(DUE24), at(Duration.ofMinutes(5), ISSUED_WEEK_BEFORE));
    }

    @Test
    void noDayReminderWhenIssuedLessThanDayBeforeDue() {
        assertTrue(at(Duration.ofHours(3), DUE.minus(Duration.ofHours(10))).isEmpty());
    }

    @Test
    void overdueRightAtDueAndWithinWeek() {
        assertEquals(Optional.of(OVERDUE), HomeworkRules.due(DUE, ISSUED_WEEK_BEFORE, DUE));
        assertEquals(Optional.of(OVERDUE), HomeworkRules.due(DUE, ISSUED_WEEK_BEFORE, DUE.plus(Duration.ofDays(6))));
    }

    @Test
    void nothingLongAfterDue() {
        // После долгого сна ноутбука о давно прошедшем сроке не пишем.
        assertTrue(HomeworkRules.due(DUE, ISSUED_WEEK_BEFORE, DUE.plus(Duration.ofDays(8))).isEmpty());
    }
}
