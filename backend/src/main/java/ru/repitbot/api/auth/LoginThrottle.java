package ru.repitbot.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Защита от подбора пароля: после {@link #MAX_PER_LOGIN} неудачных попыток для логина
 * (или {@link #MAX_PER_IP} с одного адреса) вход закрыт на {@link #WINDOW_MS}. Удачный вход сбрасывает счётчик логина.
 * Ключи — «login:anna» и «ip:1.2.3.4», в памяти: после перезапуска счёт начинается заново, это не страшно.
 */
@Component
public class LoginThrottle {

    static final int MAX_PER_LOGIN = 10;
    static final int MAX_PER_IP = 50;
    static final long WINDOW_MS = 15 * 60_000;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    private record Window(long startMs, int failures) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public LoginThrottle() {
        this(System::currentTimeMillis);
    }

    LoginThrottle(LongSupplier clock) {
        this.clock = clock;
    }

    /** Можно ли сейчас пробовать войти. */
    public boolean allowed(String login, String ip) {
        return failures("login:" + login) < MAX_PER_LOGIN && failures("ip:" + ip) < MAX_PER_IP;
    }

    public void failed(String login, String ip) {
        long now = clock.getAsLong();
        for (String key : new String[]{"login:" + login, "ip:" + ip}) {
            Window w = windows.compute(key, (k, old) -> old == null || now - old.startMs() >= WINDOW_MS
                    ? new Window(now, 1)
                    : new Window(old.startMs(), old.failures() + 1));
            if (w.failures() == (key.startsWith("ip:") ? MAX_PER_IP : MAX_PER_LOGIN)) {
                log.warn("Вход временно закрыт после неудачных попыток: {}", key.startsWith("ip:") ? "адрес" : "логин");
            }
        }
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(old -> now - old.startMs() >= WINDOW_MS);
        }
    }

    public void succeeded(String login) {
        windows.remove("login:" + login);
    }

    private int failures(String key) {
        Window w = windows.get(key);
        return w == null || clock.getAsLong() - w.startMs() >= WINDOW_MS ? 0 : w.failures();
    }
}
