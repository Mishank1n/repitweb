package ru.repitbot.domain.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** Даты и время по-русски для сообщений бота. */
public final class When {

    public static final Locale RU = Locale.of("ru");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EE, d MMMM", RU);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("EE dd.MM", RU);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.BASIC_ISO_DATE;

    private When() {
    }

    /** «сегодня», «завтра» или «пн, 20 октября». */
    public static String day(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return "сегодня";
        }
        if (date.equals(today.plusDays(1))) {
            return "завтра";
        }
        return DAY.format(date);
    }

    /** «пн 20.10» — для кнопок. */
    public static String shortDay(LocalDate date) {
        return SHORT_DAY.format(date);
    }

    /** «пн». */
    public static String dow(DayOfWeek day) {
        return day.getDisplayName(TextStyle.SHORT, RU);
    }

    public static String time(LocalTime time) {
        return TIME.format(time);
    }

    /** Дата в callback-данных: 20261020. */
    public static String compact(LocalDate date) {
        return COMPACT.format(date);
    }

    public static LocalDate parseCompact(String text) {
        return LocalDate.parse(text, COMPACT);
    }
}
