package ru.repitbot.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.domain.billing.AttendanceStatus;
import ru.repitbot.domain.billing.BillingService;
import ru.repitbot.domain.billing.PaymentInput;
import ru.repitbot.domain.billing.Subscription;
import ru.repitbot.domain.group.GroupService;
import ru.repitbot.domain.group.StudyGroup;
import ru.repitbot.domain.homework.HomeworkTitles;
import ru.repitbot.domain.homework.ItemKind;
import ru.repitbot.domain.homework.Part;
import ru.repitbot.domain.homework.Submission;
import ru.repitbot.domain.homework.SubmissionItem;
import ru.repitbot.domain.homework.SubmissionItemRepository;
import ru.repitbot.domain.homework.SubmissionService;
import ru.repitbot.domain.homework.SubmissionStatus;
import ru.repitbot.domain.material.MaterialService;
import ru.repitbot.domain.schedule.Lesson;
import ru.repitbot.domain.schedule.LessonStatus;
import ru.repitbot.domain.schedule.ScheduleService;
import ru.repitbot.domain.user.Role;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;
import ru.repitbot.notify.BillingNotifier;
import ru.repitbot.notify.HomeworkNotifier;
import ru.repitbot.notify.LessonNotifier;
import ru.repitbot.notify.SendLog;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Экраны и действия репетитора. Доступ — только репетитору (проверяет {@link ru.repitbot.api.auth.SessionAuth}).
 * Действия идут через сервисы и уведомления: ученик и родители получают
 * ровно те же сообщения, где бы ни была нажата кнопка.
 */
@Service
public class TutorView {

    private static final int UNMARKED_DAYS = 14;
    private static final int NEXT_LESSON_DAYS = 14;

    /** status: upcoming / ongoing / unmarked (закончилось, не отмечено) / done / cancelled. */
    public record TutorLesson(long id, String startsAt, int durationMin, long groupId, String group, String status,
                              long students) {
    }

    public record StudentChip(long id, String name, int used, int total) {
    }

    public record Today(List<TutorLesson> lessons, List<TutorLesson> unmarked, long queue, List<StudentChip> ending) {
    }

    /** status — P/C/F; subscription — «5/8» или null; warned — предупредил «не смогу прийти». */
    public record RosterRow(long studentId, String name, String status, String subscription, boolean warned) {
    }

    /** Часть материалов занятия с id — для удаления. */
    public record Material(long id, StudentView.PartDto part) {
    }

    /** canMove — будущее запланированное занятие: его можно перенести или отменить; series — «еженедельно, пн в 18:00». */
    public record LessonDetail(TutorLesson lesson, String place, String meetingUrl, String address, boolean canMark,
                               boolean marked, List<RosterRow> roster, List<Material> materials, boolean canMove,
                               String series) {
    }

    public record GroupStudent(long id, String name, String login, String subscription, int parents,
                               boolean consent) {
    }

    /** place — online / offline / null; next — ближайшее занятие на 14 дней или null. */
    public record GroupDto(long id, String name, String place, TutorLesson next, List<GroupStudent> students) {
    }

    /** Человек со ссылкой на него (родитель ученика, группа). */
    public record Ref(long id, String name) {
    }

    public record SubscriptionRow(String paidAt, int total, int used, int amount, String closedAt) {
    }

    public record Terms(int lessons, int amount) {
    }

    public record StudentCard(long id, String name, String login, boolean consent, boolean deletionRequested,
                              List<Ref> groups, List<Ref> parents, StudentView.SubscriptionDto subscription,
                              List<SubscriptionRow> history, long outside, Terms lastTerms,
                              List<StudentView.HomeworkRow> homework, StudentView.AttendanceDto attendance) {
    }

    public record QueueRow(long id, long studentId, String student, String homework, String group, String submittedAt,
                           boolean late, int attempt) {
    }

    public record SubmissionDetail(QueueRow submission, int maxScore, String status, Integer score, String comment,
                                   List<StudentView.PartDto> parts, boolean canReview) {
    }

    private final ScheduleService schedule;
    private final GroupService groups;
    private final UserService users;
    private final BillingService billing;
    private final BillingNotifier billingNotifier;
    private final SubmissionService submissions;
    private final SubmissionItemRepository submissionItems;
    private final HomeworkNotifier homeworkNotifier;
    private final MaterialService materials;
    private final StudentView studentView;
    private final SendLog sendLog;

    public TutorView(ScheduleService schedule, GroupService groups, UserService users, BillingService billing,
                     BillingNotifier billingNotifier, SubmissionService submissions,
                     SubmissionItemRepository submissionItems, HomeworkNotifier homeworkNotifier,
                     MaterialService materials, StudentView studentView, SendLog sendLog) {
        this.sendLog = sendLog;
        this.schedule = schedule;
        this.groups = groups;
        this.users = users;
        this.billing = billing;
        this.billingNotifier = billingNotifier;
        this.submissions = submissions;
        this.submissionItems = submissionItems;
        this.homeworkNotifier = homeworkNotifier;
        this.materials = materials;
        this.studentView = studentView;
    }

    // ---------- сегодня и неделя ----------

    @Transactional(readOnly = true)
    public Today today() {
        LocalDate today = schedule.today();
        Instant from = today.atStartOfDay(schedule.zone()).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(schedule.zone()).toInstant();
        List<StudentChip> ending = billing.endingSoon().stream()
                .map(s -> new StudentChip(s.getStudent().getId(), s.getStudent().displayName(), s.getLessonsUsed(),
                        s.getLessonsTotal()))
                .toList();
        return new Today(schedule.between(from, to).stream().map(this::lesson).toList(),
                schedule.unmarked(UNMARKED_DAYS).stream().map(this::lesson).toList(),
                submissions.queueSize(), ending);
    }

    @Transactional(readOnly = true)
    public List<TutorLesson> week() {
        Instant from = schedule.today().atStartOfDay(schedule.zone()).toInstant();
        return schedule.between(from, from.plus(Duration.ofDays(7))).stream().map(this::lesson).toList();
    }

    // ---------- занятие и отметка посещаемости ----------

    @Transactional(readOnly = true)
    public LessonDetail lessonDetail(long lessonId) {
        Lesson l = schedule.find(lessonId).orElseThrow();
        boolean cancelled = l.getStatus() == LessonStatus.CANCELLED;
        boolean canMark = !cancelled && !l.getStartsAt().isAfter(Instant.now());
        List<RosterRow> roster;
        if (canMark) {
            BillingService.Roster r = billing.roster(lessonId);
            roster = new ArrayList<>();
            for (int i = 0; i < r.students().size(); i++) {
                User s = r.students().get(i);
                roster.add(new RosterRow(s.getId(), s.displayName(), String.valueOf(r.statuses().get(i).code()), counter(s),
                        warned(lessonId, s)));
            }
        } else {
            roster = groups.students(l.getGroup().getId()).stream()
                    .map(s -> new RosterRow(s.getId(), s.displayName(), "P", counter(s), warned(lessonId, s)))
                    .toList();
        }
        StudyGroup g = l.getGroup();
        return new LessonDetail(lesson(l), place(g), g.getMeetingUrl(), g.getAddress(), canMark,
                l.getStatus() == LessonStatus.DONE, roster,
                materials.all(lessonId).stream()
                        .map(m -> new Material(m.getId(), studentView.part(m.toPart(), m.getId())))
                        .toList(),
                l.isUpcoming(Instant.now()), l.getSeries() == null ? null : LessonNotifier.seriesLine(l.getSeries()));
    }

    /** Отметки P/C/F по ученикам; события абонементов рассылаются так же. */
    public void mark(long lessonId, Map<Long, String> marks) {
        Map<Long, AttendanceStatus> statuses = new LinkedHashMap<>();
        marks.forEach((studentId, code) -> statuses.put(studentId, AttendanceStatus.decode(code, 1).get(0)));
        BillingService.Marked marked = billing.mark(lessonId, statuses);
        billingNotifier.events(marked.notices());
    }

    // ---------- ученики ----------

    @Transactional(readOnly = true)
    public List<GroupDto> groups() {
        return groups.all().stream()
                .map(g -> new GroupDto(g.getId(), g.getName(), place(g),
                        schedule.notEndedForGroups(List.of(g.getId()), NEXT_LESSON_DAYS).stream().findFirst()
                                .map(this::lesson).orElse(null),
                        groups.students(g.getId()).stream()
                        .map(s -> new GroupStudent(s.getId(), s.displayName(), s.getLogin(), counter(s),
                                groups.parentsOf(s.getId()).size(), users.canMessage(s)))
                        .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentCard student(ApiUser me, long studentId) {
        User s = users.findById(studentId).filter(u -> u.getRole() == Role.STUDENT).orElseThrow();
        Optional<Subscription> current = billing.current(studentId);
        StudentView.Home home = studentView.home(me, s);
        return new StudentCard(s.getId(), s.displayName(), s.getLogin(), users.canMessage(s),
                s.getDeleteRequestedAt() != null,
                groups.groupsOf(studentId).stream().map(g -> new Ref(g.getId(), g.getName())).toList(),
                groups.parentsOf(studentId).stream().map(p -> new Ref(p.getId(), p.displayName())).toList(),
                current.map(c -> new StudentView.SubscriptionDto(c.getLessonsUsed(), c.getLessonsTotal(), c.getAmount(),
                        c.getPaidAt().toString())).orElse(null),
                billing.history(studentId).stream()
                        .map(h -> new SubscriptionRow(h.getPaidAt().toString(), h.getLessonsTotal(), h.getLessonsUsed(),
                                h.getAmount(), h.getClosedAt() == null ? null : h.getClosedAt().toString()))
                        .toList(),
                current.isPresent() ? 0 : billing.outsideCount(studentId),
                billing.lastTerms(studentId).map(t -> new Terms(t.lessons(), t.amount())).orElse(null),
                studentView.homeworkRows(s).stream().limit(5).toList(),
                home.attendance());
    }

    /**
     * «Оплата получена». Открытый абонемент закрывается досрочно только по явному согласию (closeCurrent).
     */
    public void payment(long studentId, int lessons, int amount, boolean closeCurrent) {
        PaymentInput.validate(lessons, amount);
        if (billing.current(studentId).isPresent()) {
            if (!closeCurrent) {
                throw new IllegalStateException("У ученика ещё открыт абонемент");
            }
            billing.closeEarly(studentId);
        }
        Subscription opened = billing.open(studentId, lessons, amount);
        billingNotifier.opened(opened);
    }

    // ---------- проверка ДЗ ----------

    @Transactional(readOnly = true)
    public List<QueueRow> queue() {
        return submissions.queue().stream().map(TutorView::queueRow).toList();
    }

    @Transactional(readOnly = true)
    public SubmissionDetail submission(long submissionId) {
        Submission s = submissions.find(submissionId).orElseThrow();
        List<StudentView.PartDto> parts = s.currentItems().stream()
                .map(i -> studentView.part(i.toPart(), i.getId()))
                .toList();
        return new SubmissionDetail(queueRow(s), s.getHomework().getMaxScore(), s.getStatus().name(), s.getScore(),
                s.getComment(), parts, s.getStatus() == SubmissionStatus.SUBMITTED);
    }

    /** Оценка: ученик и родители получают «✅ ДЗ проверено». */
    public void grade(long submissionId, int score, String comment) {
        String text = comment == null || comment.isBlank() ? null : comment.strip();
        if (text != null && text.length() > HomeworkTitles.MAX_COMMENT) {
            throw new IllegalArgumentException("Комментарий длиннее " + HomeworkTitles.MAX_COMMENT + " символов");
        }
        homeworkNotifier.checked(submissions.grade(submissionId, score, text));
    }

    public void returnForRework(long submissionId, String comment) {
        homeworkNotifier.returned(submissions.returnForRework(submissionId, comment));
    }

    /** file_id фото из ответа ученика (только репетитору — путь /api/tutor/...). */
    @Transactional(readOnly = true)
    public Optional<String> submissionFile(long itemId) {
        return submissionItems.findById(itemId)
                .map(SubmissionItem::toPart)
                .filter(p -> p.kind() != ItemKind.TEXT)
                .map(Part::fileId);
    }

    // ---------- вспомогательное ----------

    TutorLesson lesson(Lesson l) {
        Instant now = Instant.now();
        String status = l.getStatus() == LessonStatus.CANCELLED ? "cancelled"
                : l.getStatus() == LessonStatus.DONE ? "done"
                : l.isOngoing(now) ? "ongoing"
                : l.hasEnded(now) ? "unmarked" : "upcoming";
        return new TutorLesson(l.getId(), l.getStartsAt().toString(), l.getDurationMin(), l.getGroup().getId(),
                l.getGroup().getName(), status, groups.studentCount(l.getGroup().getId()));
    }

    private boolean warned(long lessonId, User student) {
        return sendLog.skipped(lessonId, List.of(student.getId()));
    }

    /** «online» / «offline» / null — место занятий группы. */
    static String place(StudyGroup g) {
        return g.getLocationType() == null ? null : g.getLocationType().name().toLowerCase();
    }

    String counter(User student) {
        return billing.current(student.getId()).map(Subscription::counter).orElse(null);
    }

    private static QueueRow queueRow(Submission s) {
        return new QueueRow(s.getId(), s.getStudent().getId(), s.getStudent().displayName(), s.getHomework().getTitle(),
                s.getHomework().getGroup().getName(),
                s.getSubmittedAt() == null ? null : s.getSubmittedAt().toString(), s.isLate(), s.getAttempt());
    }
}
