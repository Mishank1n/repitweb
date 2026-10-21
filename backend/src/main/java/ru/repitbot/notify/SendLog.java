package ru.repitbot.notify;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

/**
 * Журналы «уже сделано»: напоминания по занятиям и ежедневные задачи.
 * Запись вставляется до отправки: если она уже есть, второй раз ничего не отправляется.
 */
@Repository
public class SendLog {

    public static final String SKIP = "SKIP";

    private final JdbcTemplate jdbc;

    public SendLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** true, если запись новая и действие нужно выполнить. */
    public boolean claim(long lessonId, long userId, String kind) {
        return jdbc.update("INSERT INTO reminder_log (lesson_id, user_id, kind) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                lessonId, userId, kind) == 1;
    }

    /** После переноса занятия напоминания и отметки «не смогу» начинаются заново. */
    public void clear(long lessonId) {
        jdbc.update("DELETE FROM reminder_log WHERE lesson_id = ?", lessonId);
    }

    /** Напоминание о сроке ДЗ (DUE24, OVERDUE) одному ученику: true, если ещё не отправлялось. */
    public boolean claimHomework(long homeworkId, long userId, String kind) {
        return jdbc.update("INSERT INTO homework_log (homework_id, user_id, kind) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                homeworkId, userId, kind) == 1;
    }

    /** Когда ежедневная задача была выполнена, если была. */
    public Optional<Instant> ranAt(String job, LocalDate date) {
        return jdbc.query("SELECT ran_at FROM daily_job_log WHERE job = ? AND run_date = ?",
                (rs, i) -> rs.getTimestamp(1).toInstant(), job, date).stream().findFirst();
    }

    /** Предупредил ли кто-то из этих пользователей «не смогу прийти» на занятие. */
    public boolean skipped(long lessonId, Collection<Long> userIds) {
        for (Long userId : userIds) {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM reminder_log WHERE lesson_id = ? AND user_id = ? AND kind = ?",
                    Integer.class, lessonId, userId, SKIP);
            if (n != null && n > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean claimDaily(String job, LocalDate date) {
        return jdbc.update("INSERT INTO daily_job_log (job, run_date) VALUES (?, ?) ON CONFLICT DO NOTHING",
                job, date) == 1;
    }
}
