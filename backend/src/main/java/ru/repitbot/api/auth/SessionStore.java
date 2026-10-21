package ru.repitbot.api.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Сессии = входы на устройствах. Токен (32 случайных байта) живёт только в cookie браузера,
 * в базе — его sha-256: по утёкшей базе войти нельзя. Сессия продлевается, пока ей пользуются.
 */
@Repository
public class SessionStore {

    public static final Duration TTL = Duration.ofDays(180);
    /** last_seen и срок обновляются не чаще раза в час, а не на каждом запросе. */
    private static final Duration TOUCH_EVERY = Duration.ofHours(1);
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_AGENT = 200;
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Row(long userId, Instant lastSeen) {
    }

    private final JdbcTemplate jdbc;

    public SessionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Новая сессия; возвращает токен для cookie. */
    public String create(long userId, String userAgent) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String agent = userAgent == null ? null : userAgent.length() > MAX_AGENT ? userAgent.substring(0, MAX_AGENT) : userAgent;
        jdbc.update("INSERT INTO sessions (token_hash, user_id, expires_at, user_agent) VALUES (?, ?, ?, ?)",
                hash(token), userId, Timestamp.from(Instant.now().plus(TTL)), agent);
        return token;
    }

    /** Пользователь действующей сессии; заодно продлевает её. */
    public Optional<Long> userId(String token) {
        if (token == null || token.isBlank() || token.length() > 64) {
            return Optional.empty();
        }
        String h = hash(token);
        List<Row> rows = jdbc.query("SELECT user_id, last_seen_at FROM sessions WHERE token_hash = ? AND expires_at > now()",
                (rs, i) -> new Row(rs.getLong(1), rs.getTimestamp(2).toInstant()), h);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Row row = rows.get(0);
        Instant now = Instant.now();
        if (row.lastSeen().isBefore(now.minus(TOUCH_EVERY))) {
            jdbc.update("UPDATE sessions SET last_seen_at = ?, expires_at = ? WHERE token_hash = ?",
                    Timestamp.from(now), Timestamp.from(now.plus(TTL)), h);
        }
        return Optional.of(row.userId());
    }

    public void delete(String token) {
        if (token != null && !token.isBlank()) {
            jdbc.update("DELETE FROM sessions WHERE token_hash = ?", hash(token));
        }
    }

    /** «Выйти на всех устройствах», новый пароль по ссылке. */
    public void deleteAll(long userId) {
        jdbc.update("DELETE FROM sessions WHERE user_id = ?", userId);
        // Подписки на push принадлежат устройствам: раз все устройства вышли, уведомления на них больше не идут.
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ?", userId);
    }

    /** Смена пароля в настройках: остальные устройства выходят, это остаётся. */
    public void deleteOthers(long userId, String keepToken) {
        jdbc.update("DELETE FROM sessions WHERE user_id = ? AND token_hash <> ?", userId, hash(keepToken == null ? "" : keepToken));
    }

    public int purgeExpired() {
        return jdbc.update("DELETE FROM sessions WHERE expires_at <= now()");
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
