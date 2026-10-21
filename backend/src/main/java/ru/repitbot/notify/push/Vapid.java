package ru.repitbot.notify.push;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.repitbot.config.AppProperties;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Ключи VAPID (RFC 8292): ими сайт подписывает каждый push, и push-сервис принимает уведомления только от владельца
 * ключа, к которому привязана подписка браузера. Пара создаётся при первом запуске и хранится в app_keys —
 * в бэкапе базы она тоже есть. Сменить ключ = все браузеры должны подписаться заново.
 */
@Component
public class Vapid {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final Duration TOKEN_TTL = Duration.ofHours(12);

    private final JdbcTemplate jdbc;
    private final String subject;
    private volatile ECPrivateKey privateKey;
    private volatile String publicKey;

    public Vapid(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.subject = props.siteOrigin();
    }

    /** Открытый ключ для браузера (applicationServerKey), base64url. */
    public String publicKey() {
        load();
        return publicKey;
    }

    /** Заголовок Authorization для запроса к push-сервису этой подписки. */
    public String authorization(String endpoint) throws GeneralSecurityException {
        load();
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost();
        String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        String claims = b64("{\"aud\":\"" + audience + "\",\"exp\":" + Instant.now().plus(TOKEN_TTL).getEpochSecond()
                + ",\"sub\":\"" + subject + "\"}");
        String signed = header + "." + claims;
        return "vapid t=" + signed + "." + B64.encodeToString(sign(privateKey, signed)) + ", k=" + publicKey;
    }

    /** ES256: подпись R || S по 32 байта, как требует JWT (а не DER, который JDK выдаёт по умолчанию). */
    static byte[] sign(ECPrivateKey key, String data) throws GeneralSecurityException {
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initSign(key);
        s.update(data.getBytes(StandardCharsets.US_ASCII));
        return s.sign();
    }

    private void load() {
        if (privateKey != null) {
            return;
        }
        synchronized (this) {
            if (privateKey != null) {
                return;
            }
            try {
                String priv = read("vapid_private");
                if (priv == null) {
                    KeyPair pair = PushCrypto.newKeyPair();
                    // ON CONFLICT: если ключ успел создать параллельный запуск, берём его, а не свой.
                    jdbc.update("INSERT INTO app_keys (name, value) VALUES ('vapid_private', ?), ('vapid_public', ?) "
                                    + "ON CONFLICT (name) DO NOTHING",
                            B64.encodeToString(PushCrypto.rawPrivate((ECPrivateKey) pair.getPrivate())),
                            B64.encodeToString(PushCrypto.rawPublic((ECPublicKey) pair.getPublic())));
                    priv = read("vapid_private");
                }
                publicKey = read("vapid_public");
                privateKey = PushCrypto.privateKey(B64D.decode(priv));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Не удалось подготовить ключи VAPID", e);
            }
        }
    }

    private String read(String name) {
        return jdbc.queryForList("SELECT value FROM app_keys WHERE name = ?", String.class, name).stream()
                .findFirst().orElse(null);
    }

    private static String b64(String json) {
        return B64.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
