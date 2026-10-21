package ru.repitbot.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Защита от флуда: не больше {@link #MAX_PER_WINDOW} запросов к API за {@link #WINDOW_MS} мс от одного пользователя.
 * Экран сайта делает несколько запросов подряд, поэтому лимит с запасом; репетитора он не касается.
 */
@Component
public class RateLimiter {

    static final int MAX_PER_WINDOW = 20;
    static final long WINDOW_MS = 10_000;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private record Window(long startMs, int count) {
    }

    private final Map<Long, Window> windows = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public RateLimiter() {
        this(System::currentTimeMillis);
    }

    RateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    public boolean allow(long userId) {
        long now = clock.getAsLong();
        Window w = windows.compute(userId, (id, old) -> old == null || now - old.startMs() >= WINDOW_MS
                ? new Window(now, 1)
                : new Window(old.startMs(), old.count() + 1));
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(old -> now - old.startMs() >= WINDOW_MS);
        }
        if (w.count() == MAX_PER_WINDOW + 1) {
            log.warn("Пользователь {} превысил лимит сообщений, лишние игнорируются", userId);
        }
        return w.count() <= MAX_PER_WINDOW;
    }
}
