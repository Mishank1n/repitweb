package ru.repitbot.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.repitbot.api.ApiUser;
import ru.repitbot.domain.invite.InviteService;
import ru.repitbot.domain.invite.InviteType;
import ru.repitbot.domain.user.Credentials;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;
import ru.repitbot.notify.Inbox;

import java.util.Map;
import java.util.Optional;

/**
 * Открытая часть API: вход и выход, страница приглашения, новый пароль по ссылке, первая настройка репетитора.
 * Ошибки форм — 400 с текстом для человека (см. ApiErrors).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginBody(String login, String password) {
    }

    /**
     * Форма приглашения. useCurrent — привязать к аккаунту, с которого уже вошли (второй ребёнок, вторая группа).
     * Boolean, а не boolean: отсутствующее поле — это «нет», а не ошибка разбора JSON.
     */
    public record JoinBody(String firstName, String lastName, String login, String password, Boolean consent,
                           Boolean useCurrent) {
    }

    public record PasswordBody(String password) {
    }

    public record SetupBody(String firstName, String lastName, String login, String password) {
    }

    /** Кто вошёл на этом устройстве (для «привязать к текущему аккаунту»). */
    public record Current(String firstName, String role) {
    }

    public record InviteInfo(String type, String groupName, String studentName, String tutorName, String consent,
                             Current current) {
    }

    public record ResetInfo(String firstName, String login) {
    }

    private final UserService users;
    private final InviteService invites;
    private final SessionStore sessions;
    private final SessionCookie cookie;
    private final LoginThrottle throttle;
    private final TutorSetup setup;
    private final Inbox inbox;

    public AuthController(UserService users, InviteService invites, SessionStore sessions, SessionCookie cookie,
                          LoginThrottle throttle, TutorSetup setup, Inbox inbox) {
        this.users = users;
        this.invites = invites;
        this.sessions = sessions;
        this.cookie = cookie;
        this.throttle = throttle;
        this.setup = setup;
        this.inbox = inbox;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginBody body, HttpServletRequest request,
                                                     HttpServletResponse response) {
        String login = Credentials.normalizeLogin(body.login());
        String ip = request.getRemoteAddr();
        if (!throttle.allowed(login, ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "too_many_attempts",
                    "message", "Слишком много попыток. Подождите 15 минут или попросите у репетитора ссылку для нового пароля."));
        }
        Optional<User> user = users.authenticate(login, body.password());
        if (user.isEmpty()) {
            throttle.failed(login, ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "bad_credentials",
                    "message", "Неверный логин или пароль"));
        }
        throttle.succeeded(login);
        startSession(user.get(), request, response);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.delete(SessionCookie.read(request));
        cookie.clear(response);
        return Map.of("ok", true);
    }

    // ---------- приглашения ----------

    @GetMapping("/invite/{code}")
    public InviteInfo invite(@PathVariable String code, @RequestAttribute(name = SessionAuth.ATTR, required = false) ApiUser me) {
        InviteService.Pending p = invites.findPending(code).orElseThrow(AuthController::notFound);
        String tutorName = users.findTutor().map(User::displayName).orElse(null);
        String child = p.type() == InviteType.PARENT ? p.studentName() : null;
        Current current = me == null ? null : new Current(me.firstName(), me.role().name());
        return new InviteInfo(p.type().name(), p.groupName(), p.studentName(), tutorName,
                ConsentText.render(tutorName, child), current);
    }

    @PostMapping("/invite/{code}")
    public Map<String, Boolean> join(@PathVariable String code, @RequestBody JoinBody body,
                                     @RequestAttribute(name = SessionAuth.ATTR, required = false) ApiUser me,
                                     HttpServletRequest request, HttpServletResponse response) {
        if (!Boolean.TRUE.equals(body.consent())) {
            throw new IllegalArgumentException("Отметьте согласие на обработку данных");
        }
        InviteService.Accepted accepted;
        if (Boolean.TRUE.equals(body.useCurrent()) && me != null) {
            accepted = invites.acceptExisting(code, me.userId());
        } else {
            accepted = invites.acceptNew(code, new InviteService.NewAccount(body.login(), body.password(),
                    body.firstName(), body.lastName()));
            // Новый аккаунт на устройстве, где был вход в другой: прежняя сессия закрывается.
            sessions.delete(SessionCookie.read(request));
            startSession(accepted.user(), request, response);
        }
        notifyTutor(accepted);
        return Map.of("ok", true);
    }

    // ---------- новый пароль ----------

    @GetMapping("/reset/{code}")
    public ResetInfo reset(@PathVariable String code) {
        User user = invites.findReset(code).orElseThrow(AuthController::notFound);
        return new ResetInfo(user.getFirstName(), user.getLogin());
    }

    @PostMapping("/reset/{code}")
    public Map<String, Boolean> reset(@PathVariable String code, @RequestBody PasswordBody body,
                                      HttpServletRequest request, HttpServletResponse response) {
        User user = invites.useReset(code, body.password());
        // Пароль сменился: все прежние входы (возможно, чужие) закрываются.
        sessions.deleteAll(user.getId());
        startSession(user, request, response);
        return Map.of("ok", true);
    }

    // ---------- первая настройка ----------

    @GetMapping("/setup")
    public Map<String, Boolean> setupNeeded() {
        return Map.of("needed", setup.pending());
    }

    @PostMapping("/setup/{code}")
    public Map<String, Boolean> setup(@PathVariable String code, @RequestBody SetupBody body,
                                      HttpServletRequest request, HttpServletResponse response) {
        if (!setup.pending() || !setup.matches(code)) {
            throw notFound();
        }
        User tutor = users.createTutor(body.login(), body.password(), body.firstName(), body.lastName());
        setup.done();
        startSession(tutor, request, response);
        return Map.of("ok", true);
    }

    private void startSession(User user, HttpServletRequest request, HttpServletResponse response) {
        cookie.set(response, sessions.create(user.getId(), request.getHeader("User-Agent")));
    }

    /** Репетитору: кто вошёл по ссылке. Логин виден, чтобы было понятно, тот ли это человек. */
    private void notifyTutor(InviteService.Accepted a) {
        User u = a.user();
        String who = u.displayName() + " (логин " + u.getLogin() + ")";
        if (a.type() == InviteType.STUDENT) {
            inbox.toTutor(new Inbox.Notice("🆕 Новый ученик в группе «" + a.groupName() + "»",
                    who + " присоединился(-ась) по ссылке. Если это не тот человек — уберите его из группы.",
                    "/students/" + u.getId()));
        } else {
            inbox.toTutor(new Inbox.Notice("🆕 Родитель подключён к ученику " + a.studentName(),
                    who + ". Если это не тот человек — отвяжите его в карточке ученика.",
                    "/students/" + a.studentId()));
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
