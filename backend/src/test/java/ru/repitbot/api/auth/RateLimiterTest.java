package ru.repitbot.api.auth;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final RateLimiter limiter = new RateLimiter(now::get);

    @Test
    void dropsMessagesOverLimitWithinWindow() {
        for (int i = 0; i < RateLimiter.MAX_PER_WINDOW; i++) {
            assertTrue(limiter.allow(1), "сообщение " + (i + 1));
        }
        assertFalse(limiter.allow(1), "сообщение сверх лимита");
    }

    @Test
    void allowsAgainAfterWindow() {
        for (int i = 0; i <= RateLimiter.MAX_PER_WINDOW; i++) {
            limiter.allow(1);
        }
        now.addAndGet(RateLimiter.WINDOW_MS);
        assertTrue(limiter.allow(1));
    }

    @Test
    void limitsAreIndependentPerUser() {
        for (int i = 0; i <= RateLimiter.MAX_PER_WINDOW; i++) {
            limiter.allow(1);
        }
        assertTrue(limiter.allow(2));
    }
}
