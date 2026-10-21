package ru.repitbot.api.auth;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final LoginThrottle throttle = new LoginThrottle(now::get);

    @Test
    void locksLoginAfterTooManyFailures() {
        for (int i = 0; i < LoginThrottle.MAX_PER_LOGIN; i++) {
            assertTrue(throttle.allowed("anna", "1.1.1." + i));
            throttle.failed("anna", "1.1.1." + i);
        }
        assertFalse(throttle.allowed("anna", "9.9.9.9"), "с любого адреса");
        assertTrue(throttle.allowed("petya", "9.9.9.9"), "другой логин не затронут");
    }

    @Test
    void unlocksAfterWindow() {
        for (int i = 0; i < LoginThrottle.MAX_PER_LOGIN; i++) {
            throttle.failed("anna", "1.1.1.1");
        }
        now.addAndGet(LoginThrottle.WINDOW_MS);
        assertTrue(throttle.allowed("anna", "1.1.1.1"));
    }

    @Test
    void successResetsLoginCounter() {
        for (int i = 0; i < LoginThrottle.MAX_PER_LOGIN - 1; i++) {
            throttle.failed("anna", "1.1.1.1");
        }
        throttle.succeeded("anna");
        throttle.failed("anna", "1.1.1.1");
        assertTrue(throttle.allowed("anna", "1.1.1.1"));
    }

    @Test
    void locksAddressThatTriesManyLogins() {
        for (int i = 0; i < LoginThrottle.MAX_PER_IP; i++) {
            throttle.failed("user" + i, "6.6.6.6");
        }
        assertFalse(throttle.allowed("someone", "6.6.6.6"));
        assertTrue(throttle.allowed("someone", "7.7.7.7"));
    }
}
