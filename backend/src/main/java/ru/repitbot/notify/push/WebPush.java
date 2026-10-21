package ru.repitbot.notify.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Web push: подписки браузеров (одна на устройство и браузер) и отправка уведомлений на них.
 * Отправка — в фоне: запрос пользователя не ждёт push-сервисов. Не дошло — не страшно, уведомление
 * всё равно лежит в ленте на сайте. Подписку, которую сервис объявил несуществующей (404/410), удаляем.
 */
@Component
public class WebPush {

    private static final Logger log = LoggerFactory.getLogger(WebPush.class);
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    /** Сколько устройств на человека: телефон, планшет, компьютер с парой браузеров. */
    static final int MAX_PER_USER = 10;
    /** Уведомление, которое не удалось доставить за 12 часов (телефон выключен), уже не нужно — оно есть в ленте. */
    private static final int TTL_SECONDS = 12 * 3600;
    private static final int MAX_BODY = 600;
    /**
     * Куда сервер согласен отправлять: push-сервисы браузеров. Адрес подписки присылает браузер пользователя,
     * и без этого списка через него можно было бы заставить сервер слать запросы куда угодно (SSRF).
     */
    private static final List<String> HOSTS = List.of(
            "fcm.googleapis.com", "android.googleapis.com",                 // Chrome, Edge на Android, Opera, Samsung
            ".push.apple.com",                                              // Safari, iPhone
            "updates.push.services.mozilla.com", "push.services.mozilla.com", // Firefox
            ".notify.windows.com",                                          // Edge на Windows
            ".yandex.ru", ".yandex.net");                                   // Яндекс Браузер (не проверено на телефоне)

    /** Уведомление для показа: заголовок, текст, экран сайта и счётчик непрочитанных для значка на иконке. */
    public record Message(String title, String body, String url, int unread) {
    }

    record Subscription(long id, String endpoint, byte[] p256dh, byte[] auth) {
    }

    private final JdbcTemplate jdbc;
    private final Vapid vapid;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public WebPush(JdbcTemplate jdbc, Vapid vapid) {
        this.jdbc = jdbc;
        this.vapid = vapid;
    }

    /**
     * Подписка этого браузера. Тот же адрес у другого пользователя (на устройстве сменили аккаунт)
     * переходит к текущему. Лишние старые подписки сверх {@link #MAX_PER_USER} удаляются.
     */
    public void subscribe(long userId, String endpoint, String p256dh, String auth, String userAgent) {
        if (!allowed(endpoint)) {
            log.warn("Подписка на push с неизвестного сервиса отклонена: {}", host(endpoint));
            throw new IllegalArgumentException("Этот браузер не поддерживается для уведомлений — откройте сайт в Chrome, Safari или Firefox");
        }
        try {
            if (B64D.decode(p256dh).length != 65 || B64D.decode(auth).length != 16) {
                throw new IllegalArgumentException("Неверные ключи подписки");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Неверные ключи подписки");
        }
        String ua = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 200));
        jdbc.update("""
                INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth, user_agent) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (endpoint) DO UPDATE SET user_id = EXCLUDED.user_id, p256dh = EXCLUDED.p256dh,
                    auth = EXCLUDED.auth, user_agent = EXCLUDED.user_agent, created_at = now()""",
                userId, endpoint, p256dh, auth, ua);
        jdbc.update("""
                DELETE FROM push_subscriptions WHERE user_id = ? AND id NOT IN (
                    SELECT id FROM push_subscriptions WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?)""",
                userId, userId, MAX_PER_USER);
    }

    public void unsubscribe(long userId, String endpoint) {
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND endpoint = ?", userId, endpoint);
    }

    /** «Выйти на всех устройствах», новый пароль по ссылке, отзыв согласия: уведомления на старые устройства больше не идут. */
    public void unsubscribeAll(long userId) {
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ?", userId);
    }

    public int devices(long userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM push_subscriptions WHERE user_id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }

    /** Отправить на все устройства пользователя (в фоне). Возвращает число устройств. */
    public int deliver(long userId, Message message) {
        List<Subscription> subs = jdbc.query("SELECT id, endpoint, p256dh, auth FROM push_subscriptions WHERE user_id = ?",
                (rs, i) -> new Subscription(rs.getLong(1), rs.getString(2), B64D.decode(rs.getString(3)),
                        B64D.decode(rs.getString(4))),
                userId);
        if (subs.isEmpty()) {
            return 0;
        }
        byte[] payload = json(message).getBytes(StandardCharsets.UTF_8);
        for (Subscription s : subs) {
            executor.submit(() -> send(s, payload));
        }
        return subs.size();
    }

    private void send(Subscription s, byte[] payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(s.endpoint()))
                    .timeout(Duration.ofSeconds(20))
                    .header("TTL", String.valueOf(TTL_SECONDS))
                    .header("Urgency", "normal")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", vapid.authorization(s.endpoint()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(PushCrypto.encrypt(payload, s.p256dh(), s.auth())))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String reply = response.body() == null ? "" : response.body().strip();
            reply = reply.substring(0, Math.min(reply.length(), 300));
            if (status == 404 || status == 410) {
                log.info("Push-сервис {} ответил {} — подписка больше не действует, удалена: {}", host(s.endpoint()), status, reply);
                jdbc.update("DELETE FROM push_subscriptions WHERE id = ?", s.id());
            } else if (status >= 300) {
                log.warn("Push-сервис {} ответил {}: {}", host(s.endpoint()), status, reply);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Push на {} не отправлен: {}", host(s.endpoint()), e.toString());
        }
    }

    /** https и адрес известного push-сервиса. */
    static boolean allowed(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equals(uri.getScheme()) && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                    && endpoint.length() <= 1000
                    && HOSTS.stream().anyMatch(h -> h.startsWith(".") ? host.endsWith(h) : host.equals(h));
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    /** Для журнала только сервис: полный адрес подписки — это ключ доступа к устройству. */
    private static String host(String endpoint) {
        try {
            return URI.create(endpoint).getHost();
        } catch (RuntimeException e) {
            return "?";
        }
    }

    /** Содержимое push. Текст короче, чем в ленте: push-сервисы принимают до ~4 КБ, а на экране видно пару строк. */
    static String json(Message m) {
        String body = m.body() == null ? "" : m.body();
        if (body.length() > MAX_BODY) {
            int cut = MAX_BODY - 1;
            // Не разрезать эмодзи пополам (две половинки UTF-16): получится битый символ.
            if (Character.isHighSurrogate(body.charAt(cut - 1))) {
                cut--;
            }
            body = body.substring(0, cut) + "…";
        }
        return "{\"title\":" + quote(m.title()) + ",\"body\":" + quote(body) + ",\"url\":" + quote(m.url() == null ? "/" : m.url())
                + ",\"unread\":" + m.unread() + "}";
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
