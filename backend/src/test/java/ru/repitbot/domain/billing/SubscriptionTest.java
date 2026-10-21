package ru.repitbot.domain.billing;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.repitbot.domain.billing.BillingEvent.CLOSED;
import static ru.repitbot.domain.billing.BillingEvent.LOW;

class SubscriptionTest {

    private static Subscription of(int lessons) {
        return new Subscription(null, lessons, 1000);
    }

    @Test
    void chargesUntilClosedWithLowWarningOnce() {
        Subscription s = of(3);
        assertEquals(Set.of(), s.charge());
        assertEquals(Set.of(LOW), s.charge());
        assertEquals("2/3", s.counter());
        assertTrue(s.isOpen());
        assertEquals(Set.of(CLOSED), s.charge());
        assertFalse(s.isOpen());
        assertThrows(IllegalStateException.class, s::charge);
    }

    @Test
    void singleLessonClosesWithoutLowWarning() {
        assertEquals(Set.of(CLOSED), of(1).charge());
    }

    @Test
    void refundReopensAndNotificationsAreNotRepeated() {
        Subscription s = of(3);
        s.charge();
        s.charge();
        s.charge();
        // Исправили отметку последнего занятия: абонемент снова открыт, 2/3.
        s.refund();
        assertTrue(s.isOpen());
        assertEquals("2/3", s.counter());
        // Ещё одно исправление вниз и снова вверх: «осталось 1» второй раз не приходит.
        s.refund();
        assertEquals(Set.of(), s.charge());
        // И при повторном закрытии «закончился» тоже не дублируется.
        assertEquals(Set.of(), s.charge());
        assertFalse(s.isOpen());
    }

    @Test
    void refundKeepClosedLeavesItClosed() {
        Subscription s = of(1);
        s.charge();
        s.refundKeepClosed();
        assertFalse(s.isOpen());
        assertEquals("0/1", s.counter());
        assertThrows(IllegalStateException.class, s::refundKeepClosed);
    }

    @Test
    void closeEarlyStopsCharging() {
        Subscription s = of(8);
        s.charge();
        s.closeEarly();
        assertFalse(s.isOpen());
        assertEquals("1/8", s.counter());
        assertThrows(IllegalStateException.class, s::charge);
    }

    @Test
    void rejectsInvalidTerms() {
        assertThrows(IllegalArgumentException.class, () -> new Subscription(null, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> new Subscription(null, 8, -1));
    }
}
