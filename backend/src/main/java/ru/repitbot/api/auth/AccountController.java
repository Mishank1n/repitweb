package ru.repitbot.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.repitbot.api.ApiUser;
import ru.repitbot.domain.user.Role;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;
import ru.repitbot.notify.Inbox;

import java.util.Map;

/** Настройки аккаунта: пароль, выход на всех устройствах, отзыв согласия. */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    public record ChangePasswordBody(String current, String password) {
    }

    private final UserService users;
    private final SessionStore sessions;
    private final SessionCookie cookie;
    private final Inbox inbox;

    public AccountController(UserService users, SessionStore sessions, SessionCookie cookie, Inbox inbox) {
        this.users = users;
        this.sessions = sessions;
        this.cookie = cookie;
        this.inbox = inbox;
    }

    /** Новый пароль: остальные устройства выходят, это остаётся. */
    @PostMapping("/password")
    public Map<String, Boolean> password(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @RequestBody ChangePasswordBody body,
                                         HttpServletRequest request) {
        users.changePassword(me.userId(), body.current(), body.password());
        sessions.deleteOthers(me.userId(), SessionCookie.read(request));
        return Map.of("ok", true);
    }

    @PostMapping("/logout-all")
    public Map<String, Boolean> logoutAll(@RequestAttribute(SessionAuth.ATTR) ApiUser me, HttpServletResponse response) {
        sessions.deleteAll(me.userId());
        cookie.clear(response);
        return Map.of("ok", true);
    }

    /** «Удалить мои данные»: уведомления прекращаются, вход закрывается, репетитор удаляет данные в течение 30 дней. */
    @PostMapping("/delete-me")
    public Map<String, Boolean> deleteMe(@RequestAttribute(SessionAuth.ATTR) ApiUser me, HttpServletResponse response) {
        if (me.role() == Role.TUTOR) {
            throw new IllegalArgumentException("Аккаунт репетитора так не удаляется");
        }
        User user = users.requestDeletion(me.userId());
        sessions.deleteAll(me.userId());
        cookie.clear(response);
        inbox.toTutor(new Inbox.Notice("⚠️ " + user.displayName() + " просит удалить данные",
                user.getRole().title() + " отозвал(а) согласие на обработку данных. Удалите данные в течение 30 дней.",
                "/students"));
        return Map.of("ok", true);
    }
}
