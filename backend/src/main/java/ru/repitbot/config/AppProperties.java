package ru.repitbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Настройки сайта из переменных окружения (.env).
 *
 * @param siteUrl       адрес сайта (https://… на сервере, http://localhost:8080 на ноутбуке): для ссылок-приглашений
 *                      и проверки, что запрос пришёл со своего сайта
 * @param timezone      часовой пояс по умолчанию
 * @param inviteTtlDays сколько дней действует ссылка-приглашение
 * @param digestTime    во сколько присылать репетитору утреннюю сводку, ЧЧ:ММ
 * @param reportDay     в какой день недели отправлять родителям отчёт за 7 дней (SUNDAY…)
 * @param reportTime    во сколько отправлять отчёт, ЧЧ:ММ; за час до этого — предложение дописать комментарии
 * @param filesDir      папка с файлами заданий, ответов и материалов
 */
@ConfigurationProperties("app")
public record AppProperties(
        String siteUrl,
        String timezone,
        int inviteTtlDays,
        String digestTime,
        String reportDay,
        String reportTime,
        String filesDir
) {
    public ZoneId zone() {
        return ZoneId.of(timezone == null || timezone.isBlank() ? "Europe/Moscow" : timezone);
    }

    /** Адрес сайта без «/» в конце. */
    public String site() {
        String url = siteUrl == null || siteUrl.isBlank() ? "http://localhost:8080" : siteUrl.strip();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** «https://repit.ru» — для сравнения с заголовком Origin. */
    public String siteOrigin() {
        URI uri = URI.create(site());
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    /** Cookie с флагом Secure только по https: на http://localhost браузер её иначе не сохранит. */
    public boolean secureCookies() {
        return site().startsWith("https://");
    }

    public LocalTime digestLocalTime() {
        try {
            return LocalTime.parse(digestTime);
        } catch (RuntimeException e) {
            return LocalTime.of(8, 0);
        }
    }

    public DayOfWeek reportDayOfWeek() {
        try {
            return DayOfWeek.valueOf(reportDay.strip().toUpperCase());
        } catch (RuntimeException e) {
            return DayOfWeek.SUNDAY;
        }
    }

    /** Не раньше 01:00: предложение приходит за час, в тот же день. */
    public LocalTime reportLocalTime() {
        try {
            LocalTime t = LocalTime.parse(reportTime);
            return t.isBefore(LocalTime.of(1, 0)) ? LocalTime.of(1, 0) : t;
        } catch (RuntimeException e) {
            return LocalTime.of(18, 0);
        }
    }
}
