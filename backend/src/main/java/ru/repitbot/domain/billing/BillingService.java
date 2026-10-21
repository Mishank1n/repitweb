package ru.repitbot.domain.billing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.domain.group.GroupService;
import ru.repitbot.domain.schedule.Lesson;
import ru.repitbot.domain.schedule.LessonRepository;
import ru.repitbot.domain.schedule.LessonStatus;
import ru.repitbot.domain.user.Role;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Абонементы и посещаемость. Только для репетитора: доступ закрыт маршрутизацией кнопок.
 *
 * <p>Счётчик абонемента меняется только здесь, вместе с отметками, в одной транзакции.
 * У ученика не больше одного открытого абонемента (частичный уникальный индекс в V5).
 */
@Service
public class BillingService {

    /** Кого и как отмечать на занятии: ученики в фиксированном порядке и их текущие отметки. */
    public record Roster(Lesson lesson, List<User> students, List<AttendanceStatus> statuses) {
    }

    public record Line(User student, AttendanceStatus status, Subscription subscription) {
    }

    /** События абонемента после отметки: о них нужно сообщить. */
    public record Notice(Subscription subscription, Set<BillingEvent> events) {
    }

    public record Marked(Lesson lesson, List<Line> lines, List<Notice> notices) {
    }

    private final SubscriptionRepository subscriptions;
    private final AttendanceRepository attendance;
    private final LessonRepository lessons;
    private final GroupService groups;
    private final UserService users;

    public BillingService(SubscriptionRepository subscriptions, AttendanceRepository attendance,
                          LessonRepository lessons, GroupService groups, UserService users) {
        this.subscriptions = subscriptions;
        this.attendance = attendance;
        this.lessons = lessons;
        this.groups = groups;
        this.users = users;
    }

    // ---------- абонементы ----------

    @Transactional
    public Subscription open(long studentId, int lessonsTotal, int amount) {
        User student = users.findById(studentId)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> new NoSuchElementException("Ученик не найден"));
        if (subscriptions.findByStudent_IdAndClosedAtIsNull(studentId).isPresent()) {
            throw new IllegalStateException("У ученика уже открыт абонемент");
        }
        return subscriptions.save(new Subscription(student, lessonsTotal, amount));
    }

    /** Закрыть текущий абонемент досрочно (перед новой оплатой). */
    @Transactional
    public Optional<Subscription> closeEarly(long studentId) {
        Optional<Subscription> open = subscriptions.findByStudent_IdAndClosedAtIsNull(studentId);
        open.ifPresent(Subscription::closeEarly);
        return open;
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> current(long studentId) {
        return subscriptions.findByStudent_IdAndClosedAtIsNull(studentId);
    }

    @Transactional(readOnly = true)
    public List<Subscription> history(long studentId) {
        return subscriptions.findTop5ByStudent_IdOrderByPaidAtDesc(studentId);
    }

    @Transactional(readOnly = true)
    public List<Subscription> endingSoon() {
        return subscriptions.findEndingSoon();
    }

    /** Условия последнего абонемента — для кнопки «Как в прошлый раз». */
    @Transactional(readOnly = true)
    public Optional<PaymentInput.Terms> lastTerms(long studentId) {
        return subscriptions.findTopByStudent_IdOrderByPaidAtDesc(studentId)
                .map(s -> new PaymentInput.Terms(s.getLessonsTotal(), s.getAmount()));
    }

    /** Списываемые занятия без абонемента после последней оплаты (или за всё время). */
    @Transactional(readOnly = true)
    public long outsideCount(long studentId) {
        Instant since = subscriptions.findTopByStudent_IdOrderByPaidAtDesc(studentId)
                .map(Subscription::getPaidAt)
                .orElse(Instant.EPOCH);
        return attendance.countOutside(studentId, since);
    }

    // ---------- посещаемость ----------

    /**
     * Список для отметки: у отмеченного занятия — отмеченные ученики с их отметками,
     * у неотмеченного — текущий состав группы, все «был».
     */
    @Transactional(readOnly = true)
    public Roster roster(long lessonId) {
        Lesson lesson = markableLesson(lessonId);
        List<Attendance> marked = attendance.findByLesson_IdOrderByStudent_IdAsc(lessonId);
        if (!marked.isEmpty()) {
            return new Roster(lesson, marked.stream().map(Attendance::getStudent).toList(),
                    marked.stream().map(Attendance::getStatus).toList());
        }
        List<User> students = groups.students(lesson.getGroup().getId());
        return new Roster(lesson, students, students.stream().map(s -> AttendanceStatus.PRESENT).toList());
    }

    /**
     * Сохранить отметки и пересчитать абонементы. Повторная отметка того же занятия
     * (исправление ошибки) меняет только то, что изменилось.
     */
    @Transactional
    public Marked mark(long lessonId, Map<Long, AttendanceStatus> statuses) {
        Lesson lesson = markableLesson(lessonId);
        Map<Long, Attendance> existing = new LinkedHashMap<>();
        attendance.findByLesson_IdOrderByStudent_IdAsc(lessonId).forEach(a -> existing.put(a.getStudent().getId(), a));
        long groupId = lesson.getGroup().getId();

        List<Line> lines = new ArrayList<>();
        List<Notice> notices = new ArrayList<>();
        for (Map.Entry<Long, AttendanceStatus> e : statuses.entrySet()) {
            long studentId = e.getKey();
            AttendanceStatus status = e.getValue();
            Attendance a = existing.get(studentId);
            if (a == null) {
                if (!groups.isMember(groupId, studentId)) {
                    throw new IllegalArgumentException("Ученик не состоит в группе");
                }
                User student = users.findById(studentId).orElseThrow();
                a = attendance.save(new Attendance(lesson, student, status));
                if (status.charged()) {
                    charge(a, notices);
                }
            } else {
                boolean wasCharged = a.getStatus().charged();
                if (!wasCharged && status.charged()) {
                    charge(a, notices);
                } else if (wasCharged && !status.charged()) {
                    refund(a);
                }
                a.mark(status);
            }
            lines.add(new Line(a.getStudent(), status, a.getSubscription()));
        }
        lesson.markDone();
        return new Marked(lesson, lines, notices);
    }

    private void charge(Attendance a, List<Notice> notices) {
        subscriptions.findByStudent_IdAndClosedAtIsNull(a.getStudent().getId()).ifPresent(open -> {
            a.setSubscription(open);
            Set<BillingEvent> events = open.charge();
            if (!events.isEmpty()) {
                notices.add(new Notice(open, events));
            }
        });
    }

    /**
     * Занятие больше не списывается. Если его абонемент уже закрыт, а у ученика открыт следующий,
     * в освободившееся место переносится самое раннее занятие следующего: старый остаётся
     * закрытым и полным, у следующего освобождается одно занятие.
     */
    private void refund(Attendance a) {
        Subscription sub = a.getSubscription();
        a.setSubscription(null);
        if (sub == null) {
            return;
        }
        if (sub.isOpen()) {
            sub.refund();
            return;
        }
        // Запросы — до изменений: автоматический flush не должен увидеть два открытых абонемента.
        Optional<Subscription> next = subscriptions.findByStudent_IdAndClosedAtIsNull(a.getStudent().getId());
        if (next.isEmpty()) {
            sub.refund();
            return;
        }
        Optional<Attendance> earliest = attendance.findTopBySubscription_IdOrderByLesson_StartsAtAsc(next.get().getId());
        if (earliest.isPresent()) {
            earliest.get().setSubscription(sub);
            next.get().refund();
        } else {
            sub.refundKeepClosed();
        }
    }

    private Lesson markableLesson(long lessonId) {
        Lesson lesson = lessons.findById(lessonId).orElseThrow(() -> new NoSuchElementException("Занятие не найдено"));
        if (lesson.getStatus() == LessonStatus.CANCELLED) {
            throw new IllegalStateException("Занятие отменено");
        }
        if (lesson.getStartsAt().isAfter(Instant.now())) {
            throw new IllegalStateException("Занятие ещё не началось");
        }
        return lesson;
    }
}
