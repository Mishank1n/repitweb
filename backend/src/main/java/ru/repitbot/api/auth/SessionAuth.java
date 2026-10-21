package ru.repitbot.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.repitbot.api.ApiUser;
import ru.repitbot.config.AppProperties;
import ru.repitbot.domain.user.Role;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.Set;

/**
 * Вход в API. Пользователь определяется по cookie сессии. Правила:
 * <ul>
 *   <li>/api/auth/** открыт всем (вход, приглашения, новый пароль); остальное — только после входа (401);</li>
 *   <li>изменяющие запросы принимаются только со своего сайта (заголовок Origin) — защита от CSRF;</li>
 *   <li>отозвавший согласие — 403 no_consent; /api/tutor/** — только репетитору (403 tutor_only);</li>
 *   <li>не больше 20 запросов за 10 секунд от одного пользователя (кроме репетитора).</li>
 * </ul>
 */
@Component
public class SessionAuth extends OncePerRequestFilter {

    public static final String ATTR = "apiUser";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final SessionStore sessions;
    private final UserService users;
    private final AppProperties props;
    private final RateLimiter rateLimiter = new RateLimiter();

    public SessionAuth(SessionStore sessions, UserService users, AppProperties props) {
        this.sessions = sessions;
        this.users = users;
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!SAFE_METHODS.contains(request.getMethod()) && !sameSite(request)) {
            error(response, HttpServletResponse.SC_FORBIDDEN, "bad_origin");
            return;
        }
        Optional<User> user = sessions.userId(SessionCookie.read(request)).flatMap(users::findById);
        user.ifPresent(u -> request.setAttribute(ATTR, new ApiUser(u.getId(), u.getRole(), u.getFirstName(), u.displayName())));

        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }
        if (user.isEmpty()) {
            error(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized");
            return;
        }
        User u = user.get();
        if (!users.canMessage(u)) {
            error(response, HttpServletResponse.SC_FORBIDDEN, "no_consent");
            return;
        }
        boolean tutor = u.getRole() == Role.TUTOR;
        if (path.startsWith("/api/tutor/") && !tutor) {
            error(response, HttpServletResponse.SC_FORBIDDEN, "tutor_only");
            return;
        }
        if (!tutor && !rateLimiter.allow(u.getId())) {
            error(response, 429, "too_many_requests");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Запрос со своей страницы: Origin совпадает с адресом сайта или с адресом, по которому пришёл запрос
     * (туннель на ноутбуке). Браузер всегда ставит Origin у POST из скрипта; без него — не браузер (curl), пропускаем:
     * cookie у такого клиента своя, украсть чужую сессию так нельзя.
     */
    boolean sameSite(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            return true;
        }
        if (origin.equals(props.siteOrigin())) {
            return true;
        }
        try {
            String authority = URI.create(origin).getAuthority();
            return authority != null && authority.equalsIgnoreCase(request.getHeader("Host"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void error(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + code + "\"}");
    }
}
