package ru.repitbot.notify;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Когда предлагать репетитору комментарии и когда отправлять недельные отчёты. Чистые функции:
 * время — местное время репетитора. Если ноутбук спал, отчёт догоняется до конца следующего дня,
 * и у репетитора всегда есть час на комментарии после предложения.
 */
public final class ReportRules {

    static final Duration PROMPT_AHEAD = Duration.ofHours(1);

    private ReportRules() {
    }

    /** День отчёта, к которому относится сегодняшний день: сам день отчёта или следующий за ним (догоняем). */
    public static Optional<LocalDate> reportDay(LocalDate today, DayOfWeek day) {
        if (today.getDayOfWeek() == day) {
            return Optional.of(today);
        }
        if (today.minusDays(1).getDayOfWeek() == day) {
            return Optional.of(today.minusDays(1));
        }
        return Optional.empty();
    }

    /** Отчёт за 7 дней, последний из которых — день отчёта (для воскресенья это пн–вс). */
    public static LocalDate weekStart(LocalDate reportDay) {
        return reportDay.minusDays(6);
    }

    public static boolean promptDue(LocalDateTime now, LocalDate reportDay, LocalTime time) {
        return !now.isBefore(reportDay.atTime(time).minus(PROMPT_AHEAD)) && now.isBefore(windowEnd(reportDay));
    }

    /** Когда уйдут отчёты: не раньше назначенного времени и не раньше чем через час после предложения. */
    public static LocalDateTime sendAt(LocalDate reportDay, LocalTime time, LocalDateTime promptAt) {
        LocalDateTime scheduled = reportDay.atTime(time);
        LocalDateTime afterPrompt = promptAt.plus(PROMPT_AHEAD);
        return afterPrompt.isAfter(scheduled) ? afterPrompt : scheduled;
    }

    public static boolean sendDue(LocalDateTime now, LocalDate reportDay, LocalTime time, LocalDateTime promptAt) {
        return !now.isBefore(sendAt(reportDay, time, promptAt)) && now.isBefore(windowEnd(reportDay));
    }

    private static LocalDateTime windowEnd(LocalDate reportDay) {
        return reportDay.plusDays(2).atStartOfDay();
    }
}
