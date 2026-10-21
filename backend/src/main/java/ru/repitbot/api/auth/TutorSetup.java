package ru.repitbot.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.repitbot.config.AppProperties;
import ru.repitbot.domain.user.UserService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Первая настройка сайта: пока репетитора нет, при запуске в лог пишется одноразовая ссылка /setup/CODE,
 * по которой репетитор задаёт себе логин и пароль. Код живёт только в памяти и только до создания репетитора.
 */
@Component
public class TutorSetup {

    private static final Logger log = LoggerFactory.getLogger(TutorSetup.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserService users;
    private final AppProperties props;
    private volatile String code;

    public TutorSetup(UserService users, AppProperties props) {
        this.users = users;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void announce() {
        if (users.tutorExists()) {
            return;
        }
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        log.warn("Репетитор ещё не настроен. Откройте ссылку и задайте логин и пароль: {}/setup/{}", props.site(), code);
    }

    /** Нужна ли настройка (есть действующий код). */
    public boolean pending() {
        return code != null && !users.tutorExists();
    }

    /** Сравнение за постоянное время: по времени ответа код не подобрать. */
    public boolean matches(String candidate) {
        String current = code;
        return current != null && candidate != null
                && MessageDigest.isEqual(current.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }

    public void done() {
        code = null;
    }
}
