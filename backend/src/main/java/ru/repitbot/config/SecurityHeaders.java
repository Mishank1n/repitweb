package ru.repitbot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Заголовки безопасности на всех ответах. Главный — CSP: браузер выполняет скрипты, стили и шрифты только с этого
 * сайта, поэтому даже если в текст задания или имя попадёт HTML со скриптом, он не выполнится (React и так выводит
 * текст как текст — это второй рубеж). Встроенных скриптов и стилей у сайта нет: React задаёт стили через CSSOM,
 * QR-код — атрибутами SVG, поэтому 'unsafe-inline' не нужен.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeaders extends OncePerRequestFilter {

    static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            // blob: — превью выбранных фото до загрузки; data: — мелкие картинки в стилях
            "img-src 'self' blob: data:",
            "media-src 'self' blob:",
            "font-src 'self'",
            "connect-src 'self'",
            "worker-src 'self'",
            "manifest-src 'self'",
            "object-src 'none'",
            "base-uri 'none'",
            "form-action 'self'",
            // сайт нельзя встроить в чужую страницу (защита от «невидимых» кликов)
            "frame-ancestors 'none'");

    private final boolean https;

    public SecurityHeaders(AppProperties props) {
        this.https = props.secureCookies();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        // Сайту не нужны камера (фото выбирается через системное окно), микрофон, геопозиция, оплата.
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
        if (https) {
            // Только по https: браузер больше не откроет сайт по http, даже если ввести адрес без https.
            response.setHeader("Strict-Transport-Security", "max-age=31536000");
        }
        chain.doFilter(request, response);
    }
}
