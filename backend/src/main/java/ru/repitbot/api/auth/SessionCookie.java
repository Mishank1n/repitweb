package ru.repitbot.api.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import ru.repitbot.config.AppProperties;

/**
 * Cookie сессии: HttpOnly (скрипты страницы её не видят), SameSite=Lax (чужой сайт не отправит
 * от имени пользователя POST), Secure на https.
 */
@Component
public class SessionCookie {

    public static final String NAME = "sid";

    private final AppProperties props;

    public SessionCookie(AppProperties props) {
        this.props = props;
    }

    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    public void set(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, base(token).maxAge(SessionStore.TTL).build().toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, base("").maxAge(0).build().toString());
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(props.secureCookies())
                .sameSite("Lax")
                .path("/");
    }
}
