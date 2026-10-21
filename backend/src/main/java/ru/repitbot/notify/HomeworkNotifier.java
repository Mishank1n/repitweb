package ru.repitbot.notify;

import org.springframework.stereotype.Component;
import ru.repitbot.config.AppProperties;
import ru.repitbot.domain.homework.Homework;
import ru.repitbot.domain.homework.HomeworkService;
import ru.repitbot.domain.homework.Submission;
import ru.repitbot.domain.homework.SubmissionService;
import ru.repitbot.domain.schedule.When;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;
import ru.repitbot.notify.Inbox.Notice;
import ru.repitbot.notify.Recipients.Recipient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Уведомления о ДЗ. Ученики и родители узнают о новом задании и результате, репетитор — о сданных работах.
 * Само задание открывается на сайте (родителю — только статус). Все получатели проходят проверку согласия
 * через {@link Recipients}.
 */
@Component
public class HomeworkNotifier {

    private final Recipients recipients;
    private final HomeworkService homework;
    private final SubmissionService submissions;
    private final UserService users;
    private final SendLog sendLog;
    private final Inbox inbox;
    private final ZoneId zone;

    public HomeworkNotifier(Recipients recipients, HomeworkService homework, SubmissionService submissions,
                            UserService users, SendLog sendLog, Inbox inbox, AppProperties props) {
        this.recipients = recipients;
        this.homework = homework;
        this.submissions = submissions;
        this.users = users;
        this.sendLog = sendLog;
        this.inbox = inbox;
        this.zone = props.zone();
    }

    /** ДЗ выдано: ученикам и родителям — что задано и до какого срока. Возвращает число учеников. */
    public int issued(Homework hw) {
        int students = 0;
        for (Recipient r : recipients.forStudents(hw.getAssignees())) {
            String body = LessonNotifier.forWhom(r) + title(hw) + "\nСрок: " + dueText(hw);
            if (!r.isParent()) {
                students++;
                body += "\nМаксимум: " + hw.getMaxScore() + " " + points(hw.getMaxScore());
            }
            inbox.send(r.user(), new Notice("📚 Новое домашнее задание", body, url(hw)));
        }
        return students;
    }

    public void submitted(Submission s) {
        Homework hw = s.getHomework();
        String late = s.isLate() ? "\n⚠️ Сдано после срока" : "";
        String attempt = s.getAttempt() > 1 ? "\nПопытка " + s.getAttempt() : "";
        inbox.toTutor(new Notice("📥 " + s.getStudent().displayName() + " сдал(а) ДЗ",
                title(hw) + attempt + late, "/review/" + s.getId()));
    }

    /** Проверено: ученику и его родителям баллы и комментарий. */
    public void checked(Submission s) {
        Homework hw = s.getHomework();
        String result = title(hw) + (s.getComment() == null ? "" : "\n💬 " + s.getComment());
        for (Recipient r : recipients.forStudents(List.of(s.getStudent()))) {
            inbox.send(r.user(), new Notice("✅ ДЗ проверено: " + s.getScore() + "/" + hw.getMaxScore(),
                    LessonNotifier.forWhom(r) + result, url(hw)));
        }
    }

    /** На доработку: только ученику. */
    public void returned(Submission s) {
        User student = s.getStudent();
        if (!users.canMessage(student)) {
            return;
        }
        inbox.send(student, new Notice("🔁 ДЗ нужно доработать",
                title(s.getHomework()) + "\n💬 " + s.getComment(), url(s.getHomework())));
    }

    /** Напоминания о сроке: за сутки — ученику, после срока — ученику и родителям. Каждое один раз. */
    public void sendDueReminders(Instant now) {
        Duration window = HomeworkRules.OVERDUE_WINDOW;
        for (Homework hw : homework.dueBetween(now.minus(window), now.plus(HomeworkRules.DAY).plusSeconds(300))) {
            HomeworkRules.due(hw.getDueAt(), hw.getIssuedAt(), now).ifPresent(kind -> {
                for (User student : hw.getAssignees()) {
                    if (SubmissionService.handedIn(submissions.forStudent(hw.getId(), student.getId()))) {
                        continue;
                    }
                    if (users.canMessage(student) && sendLog.claimHomework(hw.getId(), student.getId(), kind.name())) {
                        remind(hw, student, kind);
                    }
                }
            });
        }
    }

    private void remind(Homework hw, User student, HomeworkRules.Kind kind) {
        for (Recipient r : recipients.forStudents(List.of(student))) {
            if (!r.isParent()) {
                Notice n = kind == HomeworkRules.Kind.DUE24
                        ? new Notice("⏰ Завтра срок сдачи ДЗ", title(hw) + "\nСрок: " + dueText(hw), url(hw))
                        : new Notice("⏳ Срок сдачи ДЗ прошёл", title(hw)
                        + "\nСдать можно и сейчас, работа будет отмечена как поздняя.", url(hw));
                inbox.send(r.user(), n);
            } else if (kind == HomeworkRules.Kind.OVERDUE) {
                inbox.send(r.user(), new Notice("⏳ ДЗ не сдано в срок",
                        LessonNotifier.forWhom(r) + title(hw) + "\nСрок был: " + dueText(hw), url(hw)));
            }
        }
    }

    /** «Квадратные уравнения» — группа «Анна ЕГЭ». */
    public static String title(Homework hw) {
        return "«" + hw.getTitle() + "» — группа «" + hw.getGroup().getName() + "»";
    }

    /** «до ср, 22 октября в 18:00» или «без срока». */
    public String dueText(Homework hw) {
        if (hw.getDueAt() == null) {
            return "без срока";
        }
        ZonedDateTime z = hw.getDueAt().atZone(zone);
        return "до " + When.day(z.toLocalDate(), LocalDate.now(zone)) + " в " + When.time(z.toLocalTime());
    }

    public static String points(int n) {
        int mod100 = n % 100;
        int mod10 = n % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return "баллов";
        }
        return mod10 == 1 ? "балл" : mod10 >= 2 && mod10 <= 4 ? "балла" : "баллов";
    }

    private static String url(Homework hw) {
        return "/hw/" + hw.getId();
    }
}
