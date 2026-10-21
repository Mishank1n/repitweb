package ru.repitbot.notify;

import org.springframework.stereotype.Component;
import ru.repitbot.domain.billing.BillingEvent;
import ru.repitbot.domain.billing.BillingService;
import ru.repitbot.domain.billing.PaymentInput;
import ru.repitbot.domain.billing.Subscription;
import ru.repitbot.domain.user.User;
import ru.repitbot.notify.Inbox.Notice;
import ru.repitbot.notify.Recipients.Recipient;

import java.util.List;

/**
 * Уведомления об абонементах. Сумму видят только репетитор и родители, ученик — только счётчик.
 * Ученики и родители проходят проверку согласия через {@link Recipients}.
 */
@Component
public class BillingNotifier {

    private final Recipients recipients;
    private final Inbox inbox;

    public BillingNotifier(Recipients recipients, Inbox inbox) {
        this.recipients = recipients;
        this.inbox = inbox;
    }

    public void opened(Subscription s) {
        for (Recipient r : forStudent(s)) {
            String body = r.isParent()
                    ? LessonNotifier.forWhom(r) + "Абонемент: " + terms(s) + "\nПроведено: " + s.counter()
                    : "Абонемент: " + s.counter() + " занятий проведено";
            inbox.send(r.user(), new Notice("🎫 Оплата получена", body, "/"));
        }
    }

    public void events(List<BillingService.Notice> notices) {
        for (BillingService.Notice n : notices) {
            if (n.events().contains(BillingEvent.LOW)) {
                low(n.subscription());
            }
            if (n.events().contains(BillingEvent.CLOSED)) {
                closed(n.subscription());
            }
        }
    }

    /** Осталось одно занятие: репетитору и родителям. */
    private void low(Subscription s) {
        User student = s.getStudent();
        inbox.toTutor(new Notice("🎫 " + student.displayName() + ": в абонементе осталось 1 занятие",
                "Проведено " + s.counter(), tutorUrl(student)));
        for (Recipient r : forStudent(s)) {
            if (r.isParent()) {
                inbox.send(r.user(), new Notice("🎫 В абонементе осталось 1 занятие",
                        LessonNotifier.forWhom(r) + "Проведено " + s.counter(), "/"));
            }
        }
    }

    /** Абонемент закончился: репетитору (оплата — в карточке ученика), ученику и родителям — итог. */
    private void closed(Subscription s) {
        User student = s.getStudent();
        inbox.toTutor(new Notice("🎫 У " + student.displayName() + " закончился абонемент",
                "Проведено " + s.counter() + ". Когда получите оплату, откройте новый абонемент в карточке ученика.",
                tutorUrl(student)));
        String body = "Проведено " + s.getLessonsUsed() + " из " + s.getLessonsTotal() + " занятий";
        for (Recipient r : forStudent(s)) {
            inbox.send(r.user(), new Notice("🎫 Абонемент закончился", LessonNotifier.forWhom(r) + body, "/"));
        }
    }

    private List<Recipient> forStudent(Subscription s) {
        return recipients.forStudents(List.of(s.getStudent()));
    }

    private static String tutorUrl(User student) {
        return "/students/" + student.getId();
    }

    /** «8 занятий, 12 000 ₽». */
    public static String terms(Subscription s) {
        return terms(s.getLessonsTotal(), s.getAmount());
    }

    public static String terms(int lessons, int amount) {
        return lessons + " " + lessonsWord(lessons) + ", " + PaymentInput.money(amount);
    }

    static String lessonsWord(int n) {
        int mod100 = n % 100;
        int mod10 = n % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return "занятий";
        }
        return mod10 == 1 ? "занятие" : mod10 >= 2 && mod10 <= 4 ? "занятия" : "занятий";
    }
}
