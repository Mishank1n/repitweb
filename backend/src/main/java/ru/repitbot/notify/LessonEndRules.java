package ru.repitbot.notify;

import ru.repitbot.domain.schedule.Lesson;

import java.time.Duration;
import java.time.Instant;

/** Пора ли сообщить репетитору, что занятие закончилось. Чистая функция, без БД: удобно тестировать. */
public final class LessonEndRules {

    /** После долгого сна ноутбука о давно закончившихся занятиях не пишем. */
    static final Duration WINDOW = Duration.ofHours(6);
    /** Самое длинное занятие (CHECK в V2): с таким запасом выбираем занятия из БД. */
    static final Duration MAX_DURATION = Lesson.MAX_DURATION;

    private LessonEndRules() {
    }

    public static Instant end(Instant startsAt, int durationMin) {
        return startsAt.plus(Duration.ofMinutes(durationMin));
    }

    public static boolean ended(Instant startsAt, int durationMin, Instant now) {
        Instant end = end(startsAt, durationMin);
        return !now.isBefore(end) && now.isBefore(end.plus(WINDOW));
    }
}
