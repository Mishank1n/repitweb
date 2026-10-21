package ru.repitbot.notify;

import org.springframework.stereotype.Component;
import ru.repitbot.config.AppProperties;
import ru.repitbot.domain.group.StudyGroup;
import ru.repitbot.domain.schedule.Lesson;
import ru.repitbot.domain.schedule.LessonSeries;
import ru.repitbot.domain.schedule.ScheduleService;
import ru.repitbot.domain.schedule.When;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;
import ru.repitbot.notify.Inbox.Notice;
import ru.repitbot.notify.Recipients.Recipient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * Уведомления участникам группы о занятиях: напоминания, новые, перенесённые, отменённые.
 * Получатели — ученики группы и их родители, только с действующим согласием.
 */
@Component
public class LessonNotifier {

    /** Запись в reminder_log: репетитору уже сообщили, что занятие закончилось. */
    static final String ENDED = "ENDED";
    private static final String SCHEDULE = "/schedule";

    private final Recipients recipients;
    private final Inbox inbox;
    private final SendLog sendLog;
    private final UserService users;
    private final ZoneId zone;

    public LessonNotifier(Recipients recipients, Inbox inbox, SendLog sendLog, UserService users, AppProperties props) {
        this.recipients = recipients;
        this.inbox = inbox;
        this.sendLog = sendLog;
        this.users = users;
        this.zone = props.zone();
    }

    public List<Recipient> recipients(long groupId) {
        return recipients.forGroup(groupId);
    }

    /** Напоминание за сутки или за час. «Не смогу прийти» — на экране расписания. */
    public void sendReminder(Lesson lesson, ReminderRules.Kind kind) {
        String title = kind == ReminderRules.Kind.H1 ? "⏰ Через час занятие" : "⏰ Напоминание о занятии";
        for (Recipient r : recipients(lesson.getGroup().getId())) {
            if (sendLog.claim(lesson.getId(), r.user().getId(), kind.name())) {
                inbox.send(r.user(), new Notice(title, forWhom(r) + lessonLine(lesson) + location(lesson.getGroup()), SCHEDULE));
            }
        }
    }

    public void lessonCreated(Lesson lesson) {
        broadcast(lesson.getGroup(), "🆕 Новое занятие", r -> forWhom(r) + lessonLine(lesson) + location(lesson.getGroup()));
    }

    public void lessonMoved(ScheduleService.Moved moved) {
        Lesson lesson = moved.lesson();
        broadcast(lesson.getGroup(), "🔁 Занятие перенесено", r -> forWhom(r)
                + "«" + lesson.getGroup().getName() + "»\n"
                + "было: " + dayTime(moved.oldStart()) + "\n"
                + "стало: " + dayTime(lesson.getStartsAt()) + ", " + lesson.getDurationMin() + " мин"
                + location(lesson.getGroup()));
    }

    public void lessonCancelled(Lesson lesson) {
        broadcast(lesson.getGroup(), "❌ Занятие отменено", r -> forWhom(r)
                + "«" + lesson.getGroup().getName() + "», " + dayTime(lesson.getStartsAt()));
    }

    public void seriesCreated(LessonSeries s) {
        broadcast(s.getGroup(), "🆕 Новое регулярное занятие", r -> forWhom(r)
                + "«" + s.getGroup().getName() + "»: " + seriesLine(s) + location(s.getGroup()));
    }

    public void seriesRemoved(LessonSeries s) {
        broadcast(s.getGroup(), "❌ Регулярное занятие больше не проводится", r -> forWhom(r)
                + "«" + s.getGroup().getName() + "»: " + seriesLine(s));
    }

    /** Группа удалена: получателей собирают до удаления, потому что после него состава уже нет. */
    public void groupClosed(String groupName, List<Recipient> recipients) {
        for (Recipient r : recipients) {
            inbox.send(r.user(), new Notice("❌ Группа «" + groupName + "» закрыта",
                    forWhom(r) + "Будущие занятия этой группы отменены.", SCHEDULE));
        }
    }

    /** Репетитору после конца занятия: отметить посещаемость, материалы и ДЗ. Один раз на занятие. */
    public void lessonEnded(Lesson lesson) {
        users.findTutor().ifPresent(tutor -> {
            if (!sendLog.claim(lesson.getId(), tutor.getId(), ENDED)) {
                return;
            }
            ZonedDateTime start = lesson.getStartsAt().atZone(zone);
            ZonedDateTime end = LessonEndRules.end(lesson.getStartsAt(), lesson.getDurationMin()).atZone(zone);
            inbox.send(tutor, new Notice("🔔 Занятие закончилось",
                    "«" + lesson.getGroup().getName() + "», " + When.day(start.toLocalDate(), LocalDate.now(zone)) + ", "
                            + When.time(start.toLocalTime()) + "–" + When.time(end.toLocalTime())
                            + "\nОтметьте посещаемость — занятие спишется из абонементов. "
                            + "Там же можно добавить материалы и выдать ДЗ.",
                    "/lesson/" + lesson.getId()));
        });
    }

    /** «Не смогу прийти»: репетитору — кто не придёт, на какое занятие и кто сообщил, если это родитель. */
    public void skipped(Lesson lesson, User student, User reporter) {
        String by = reporter.getId().equals(student.getId()) ? "" : "\nСообщил(а) родитель: " + reporter.displayName();
        inbox.toTutor(new Notice("🙅 " + student.displayName() + " не сможет прийти", lessonLine(lesson) + by,
                "/lesson/" + lesson.getId()));
    }

    /** «завтра в 18:00 — «Группа», 60 мин». */
    public String lessonLine(Lesson lesson) {
        return dayTime(lesson.getStartsAt()) + " — «" + lesson.getGroup().getName() + "», " + lesson.getDurationMin() + " мин";
    }

    /** «завтра в 18:00». */
    public String dayTime(Instant instant) {
        ZonedDateTime z = instant.atZone(zone);
        return When.day(z.toLocalDate(), LocalDate.now(zone)) + " в " + When.time(z.toLocalTime());
    }

    public static String seriesLine(LessonSeries s) {
        return "еженедельно, " + When.dow(s.getDay()) + " в " + When.time(s.getStartTime()) + ", " + s.getDurationMin() + " мин";
    }

    /** Строка с местом занятия или пустая строка, если место не задано. */
    public static String location(StudyGroup group) {
        if (group.getLocationType() == null) {
            return "";
        }
        return switch (group.getLocationType()) {
            case ONLINE -> "\n💻 Ссылка: " + group.getMeetingUrl();
            case OFFLINE -> "\n🏠 Адрес: " + group.getAddress();
        };
    }

    private void broadcast(StudyGroup group, String title, Function<Recipient, String> body) {
        for (Recipient r : recipients(group.getId())) {
            inbox.send(r.user(), new Notice(title, body.apply(r), SCHEDULE));
        }
    }

    /** «👶 Иван\n» для родителя, пусто для ученика. */
    public static String forWhom(Recipient r) {
        return r.isParent() ? "👶 " + String.join(", ", r.children()) + "\n" : "";
    }
}
