package ru.repitbot.notify;

import org.springframework.stereotype.Component;
import ru.repitbot.domain.material.MaterialService;
import ru.repitbot.domain.schedule.Lesson;
import ru.repitbot.notify.Inbox.Notice;
import ru.repitbot.notify.Recipients.Recipient;

/**
 * Уведомление о материалах занятия: ученикам группы и их родителям (с проверкой согласия
 * через {@link Recipients}). Сами материалы открываются на сайте.
 */
@Component
public class MaterialsNotifier {

    /** Сколько человек получили уведомление. */
    public record Sent(int students, int parents) {
    }

    private final Recipients recipients;
    private final LessonNotifier lessons;
    private final Inbox inbox;

    public MaterialsNotifier(Recipients recipients, LessonNotifier lessons, Inbox inbox) {
        this.recipients = recipients;
        this.lessons = lessons;
        this.inbox = inbox;
    }

    public Sent published(MaterialService.Published p) {
        Lesson lesson = p.lesson();
        String title = p.firstTime() ? "📖 Материалы к занятию" : "📖 К занятию добавлены новые материалы";
        String body = lessonTitle(lesson) + "\nЧастей: " + p.added();
        int students = 0;
        int parents = 0;
        for (Recipient r : recipients.forGroup(lesson.getGroup().getId())) {
            inbox.send(r.user(), new Notice(title, LessonNotifier.forWhom(r) + body, "/materials/" + lesson.getId()));
            if (r.isParent()) {
                parents++;
            } else {
                students++;
            }
        }
        return new Sent(students, parents);
    }

    /** «Группа», сегодня в 18:00. */
    public String lessonTitle(Lesson lesson) {
        return "«" + lesson.getGroup().getName() + "», " + lessons.dayTime(lesson.getStartsAt());
    }
}
