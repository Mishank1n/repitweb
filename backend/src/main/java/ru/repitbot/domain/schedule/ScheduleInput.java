package ru.repitbot.domain.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор полей форм расписания с сайта. Ошибка — IllegalArgumentException с текстом для человека
 * (API показывает его как есть). Что дата не в прошлом, проверяет ScheduleService.
 */
public final class ScheduleInput {

    public static final int MIN_DURATION = 15;
    public static final int MAX_DURATION = 300;
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");

    private ScheduleInput() {
    }

    public static int duration(Integer minutes) {
        if (minutes == null || minutes < MIN_DURATION || minutes > MAX_DURATION) {
            throw new IllegalArgumentException("Длительность — от " + MIN_DURATION + " до " + MAX_DURATION + " минут");
        }
        return minutes;
    }

    /** 1 — понедельник … 7 — воскресенье. */
    public static DayOfWeek day(Integer day) {
        if (day == null || day < 1 || day > 7) {
            throw new IllegalArgumentException("Выберите день недели");
        }
        return DayOfWeek.of(day);
    }

    /** «18:00» или «9:30». */
    public static LocalTime time(String text) {
        Matcher m = text == null ? null : TIME.matcher(text.strip());
        if (m == null || !m.matches()) {
            throw new IllegalArgumentException("Укажите время, например 18:00");
        }
        int h = Integer.parseInt(m.group(1));
        int min = Integer.parseInt(m.group(2));
        if (h > 23 || min > 59) {
            throw new IllegalArgumentException("Укажите время, например 18:00");
        }
        return LocalTime.of(h, min);
    }

    /** «2026-10-25» (так дату отдаёт поле календаря в браузере). */
    public static LocalDate date(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Выберите дату");
        }
    }
}
