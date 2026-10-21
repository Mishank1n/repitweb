package ru.repitbot.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.repitbot.api.auth.SessionStore;
import ru.repitbot.config.AppProperties;
import ru.repitbot.domain.file.FileService;
import ru.repitbot.domain.homework.SubmissionService;
import ru.repitbot.domain.schedule.Lesson;
import ru.repitbot.domain.schedule.ScheduleService;
import ru.repitbot.domain.schedule.When;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;
import ru.repitbot.notify.Inbox.Notice;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Задачи раз в день: продление регулярных занятий, автоудаление данных, утренняя сводка репетитору.
 * Каждая выполняется один раз за день (журнал daily_job_log), даже если сервер был выключен в нужное время.
 */
@Component
public class DailyJobs {

    private static final Logger log = LoggerFactory.getLogger(DailyJobs.class);

    /** Сводку есть смысл присылать в течение нескольких часов после назначенного времени, не позже. */
    private static final Duration DIGEST_WINDOW = Duration.ofHours(4);
    /** Запрос на удаление по тексту согласия выполняется за 30 дней; напоминаем заранее. */
    private static final Duration DELETION_WARN_AFTER = Duration.ofDays(20);
    /** Неотмеченные занятия старше этого в сводке не показываем. */
    private static final int UNMARKED_DAYS = 14;
    /** По тексту согласия данные хранятся год после последнего занятия. */
    private static final Duration RETENTION = Duration.ofDays(365);

    private final SendLog sendLog;
    private final SubmissionService submissions;
    private final ScheduleService schedule;
    private final UserService users;
    private final JdbcTemplate jdbc;
    private final SessionStore sessions;
    private final FileService files;
    private final Inbox inbox;
    private final AppProperties props;

    public DailyJobs(SendLog sendLog, SubmissionService submissions, ScheduleService schedule, UserService users, JdbcTemplate jdbc,
                     SessionStore sessions, FileService files, Inbox inbox, AppProperties props) {
        this.sendLog = sendLog;
        this.submissions = submissions;
        this.schedule = schedule;
        this.users = users;
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.files = files;
        this.inbox = inbox;
        this.props = props;
    }

    public void runDue() {
        ZonedDateTime now = ZonedDateTime.now(props.zone());
        LocalDate today = now.toLocalDate();

        if (sendLog.claimDaily("SERIES", today)) {
            int created = schedule.extendAllSeries();
            log.info("Регулярные занятия продлены: создано {}", created);
        }
        if (sendLog.claimDaily("CLEANUP", today)) {
            cleanup();
        }
        LocalTime digestAt = props.digestLocalTime();
        LocalTime time = now.toLocalTime();
        boolean inWindow = !time.isBefore(digestAt) && Duration.between(digestAt, time).compareTo(DIGEST_WINDOW) < 0;
        if (inWindow && sendLog.claimDaily("DIGEST", today)) {
            sendDigest(today);
        }
    }

    void sendDigest(LocalDate today) {
        List<Lesson> lessons = schedule.onDay(today);
        List<User> pending = users.deletionRequestsOlderThan(DELETION_WARN_AFTER);
        long toReview = submissions.queueSize();
        List<Lesson> unmarked = schedule.unmarked(UNMARKED_DAYS);
        if (lessons.isEmpty() && pending.isEmpty() && toReview == 0 && unmarked.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (lessons.isEmpty()) {
            sb.append("Занятий сегодня нет.\n");
        } else {
            sb.append("Занятия (").append(lessons.size()).append("):\n");
            for (Lesson l : lessons) {
                sb.append("• ").append(When.time(l.getStartsAt().atZone(props.zone()).toLocalTime()))
                        .append(" — «").append(l.getGroup().getName()).append("», ")
                        .append(l.getDurationMin()).append(" мин\n");
            }
        }
        if (!unmarked.isEmpty()) {
            sb.append("\n⚠️ Не отмечено занятий: ").append(unmarked.size()).append(" — пока посещаемость "
                    + "не отмечена, занятие не списывается из абонементов.\n");
        }
        if (toReview > 0) {
            sb.append("\n📥 Ждут проверки работ: ").append(toReview).append('\n');
        }
        if (!pending.isEmpty()) {
            sb.append("\n⚠️ Запросы на удаление данных ждут больше 20 дней. ")
                    .append("По тексту согласия их нужно выполнить в течение 30 дней:\n");
            for (User u : pending) {
                sb.append("• ").append(u.displayName()).append(" (").append(u.getRole().title()).append(")\n");
            }
        }
        inbox.toTutor(new Notice("☀️ Сводка на сегодня", sb.toString().strip(), "/"));
    }

    /** Удаляет учеников без занятий больше года и родителей, у которых не осталось привязанных детей. */
    void cleanup() {
        int expired = sessions.purgeExpired();
        int notices = inbox.purgeOld();
        int orphanFiles = files.purgeOrphans();
        if (expired + notices + orphanFiles > 0) {
            log.info("Удалено истёкших сессий: {}, старых уведомлений: {}, непривязанных файлов: {}",
                    expired, notices, orphanFiles);
        }

        // Брошенные черновики ДЗ (репетитор начал и не выдал) — через неделю.
        int drafts = jdbc.update("DELETE FROM homework WHERE status = 'DRAFT' AND created_at < now() - interval '7 days'");
        if (drafts > 0) {
            log.info("Удалено брошенных черновиков ДЗ: {}", drafts);
        }

        Timestamp yearAgo = Timestamp.from(Instant.now().minus(RETENTION));
        List<Long> inactiveStudents = jdbc.queryForList("""
                SELECT u.id FROM users u
                WHERE u.role = 'STUDENT'
                  AND u.created_at < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM group_members gm
                      JOIN lessons l ON l.group_id = gm.group_id
                      WHERE gm.student_id = u.id AND l.status <> 'CANCELLED' AND l.starts_at > ?)
                """, Long.class, yearAgo, yearAgo);

        List<String> report = new ArrayList<>();
        for (Long id : inactiveStudents) {
            users.delete(id).ifPresent(u -> report.add(u.displayName() + " — больше года без занятий"));
        }

        List<Long> orphanParents = jdbc.queryForList("""
                SELECT u.id FROM users u
                WHERE u.role = 'PARENT'
                  AND NOT EXISTS (SELECT 1 FROM parent_links pl WHERE pl.parent_id = u.id)
                """, Long.class);
        for (Long id : orphanParents) {
            Optional<User> deleted = users.delete(id);
            deleted.ifPresent(u -> report.add(u.displayName() + " — родитель без привязанных учеников"));
        }

        if (!report.isEmpty()) {
            log.info("Автоудаление: удалено {} пользователей", report.size());
            inbox.toTutor(new Notice("🧹 Автоудаление данных (по тексту согласия)",
                    "• " + String.join("\n• ", report), "/"));
        }
    }
}
