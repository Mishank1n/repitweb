package ru.repitbot.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.domain.billing.Attendance;
import ru.repitbot.domain.billing.AttendanceRepository;
import ru.repitbot.domain.billing.AttendanceStatus;
import ru.repitbot.domain.billing.BillingService;
import ru.repitbot.domain.billing.Subscription;
import ru.repitbot.domain.file.FileService;
import ru.repitbot.domain.group.GroupService;
import ru.repitbot.domain.group.StudyGroup;
import ru.repitbot.domain.homework.Homework;
import ru.repitbot.domain.homework.HomeworkItem;
import ru.repitbot.domain.homework.HomeworkItemRepository;
import ru.repitbot.domain.homework.HomeworkService;
import ru.repitbot.domain.homework.HomeworkStatus;
import ru.repitbot.domain.homework.ItemKind;
import ru.repitbot.domain.homework.Part;
import ru.repitbot.domain.homework.Submission;
import ru.repitbot.domain.homework.SubmissionItem;
import ru.repitbot.domain.homework.SubmissionItemRepository;
import ru.repitbot.domain.homework.SubmissionService;
import ru.repitbot.domain.homework.SubmissionStatus;
import ru.repitbot.domain.material.LessonMaterial;
import ru.repitbot.domain.material.MaterialService;
import ru.repitbot.domain.schedule.Lesson;
import ru.repitbot.domain.schedule.ScheduleService;
import ru.repitbot.domain.user.Role;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;
import ru.repitbot.notify.SendLog;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Данные экранов ученика. Смотреть их может сам ученик, его родитель и репетитор —
 * кто именно, решает {@link #student}. Сущности наружу не отдаются, только DTO.
 * Родитель не видит содержимое заданий (только статусы и оценки) и не видит ответы.
 */
@Service
public class StudentView {

    private static final int SCHEDULE_DAYS = 14;
    private static final int NEXT_LESSON_DAYS = 60;
    private static final int MATERIAL_LESSONS = 20;

    /** skipped — ученик или родитель уже предупредил «не смогу прийти»; canSkip — занятие ещё не началось. */
    public record LessonDto(long id, String startsAt, int durationMin, String group, String place, String meetingUrl,
                            String address, boolean ongoing, boolean skipped, boolean canSkip) {
    }

    public record SubscriptionDto(int used, int total, Integer amount, String paidAt) {
    }

    /** status: TODO (не сдано) / SUBMITTED / RETURNED / CHECKED. */
    public record HomeworkRow(long id, String title, String group, String issuedAt, String dueAt, boolean overdue,
                              int maxScore, String status, Integer score, String comment) {
    }

    /** kind — TEXT, PHOTO, DOCUMENT…; mediaId — у файлов: превью фото и скачивание остального; name — имя файла. */
    public record PartDto(String kind, String text, Long mediaId, String name) {
    }

    /**
     * answer — ответ ученика последней попытки (только самому ученику; файлы — /api/media/sub/{mediaId}),
     * attempt — её номер.
     */
    public record HomeworkDetail(HomeworkRow homework, List<PartDto> parts, boolean contentHidden, boolean canSubmit,
                                 List<PartDto> answer, int attempt) {
    }

    /** Посещаемость за текущий месяц: сколько отмеченных занятий и на скольких был. */
    public record AttendanceDto(int present, int total) {
    }

    public record Home(LessonDto next, SubscriptionDto subscription, int homeworkTodo, String nearestDue,
                       List<HomeworkRow> homework, AttendanceDto attendance) {
    }

    public record MaterialLesson(long lessonId, String startsAt, String group, long parts) {
    }

    public record MaterialDetail(MaterialLesson lesson, List<PartDto> parts) {
    }

    private final UserService users;
    private final GroupService groups;
    private final ScheduleService schedule;
    private final HomeworkService homework;
    private final SubmissionService submissions;
    private final MaterialService materials;
    private final BillingService billing;
    private final HomeworkItemRepository homeworkItems;
    private final SubmissionItemRepository submissionItems;
    private final AttendanceRepository attendance;
    private final FileService files;
    private final SendLog sendLog;

    public StudentView(UserService users, GroupService groups, ScheduleService schedule, HomeworkService homework,
                       SubmissionService submissions, MaterialService materials, BillingService billing,
                       HomeworkItemRepository homeworkItems, SubmissionItemRepository submissionItems,
                       AttendanceRepository attendance, FileService files, SendLog sendLog) {
        this.homeworkItems = homeworkItems;
        this.submissionItems = submissionItems;
        this.attendance = attendance;
        this.files = files;
        this.sendLog = sendLog;
        this.users = users;
        this.groups = groups;
        this.schedule = schedule;
        this.homework = homework;
        this.submissions = submissions;
        this.materials = materials;
        this.billing = billing;
    }

    /** Ученик, данные которого может смотреть me: он сам, его ребёнок или любой ученик для репетитора. */
    @Transactional(readOnly = true)
    public Optional<User> student(ApiUser me, long studentId) {
        Optional<User> student = users.findById(studentId)
                .filter(u -> u.getRole() == Role.STUDENT && users.canMessage(u));
        return student.filter(s -> switch (me.role()) {
            case STUDENT -> me.userId() == studentId;
            case PARENT -> groups.childrenOf(me.userId()).stream().anyMatch(c -> c.getId() == studentId);
            case TUTOR -> true;
        });
    }

    @Transactional(readOnly = true)
    public Home home(ApiUser me, User student) {
        List<Lesson> upcoming = schedule.notEndedForGroups(groupIds(student), NEXT_LESSON_DAYS);
        List<HomeworkRow> rows = homeworkRows(student);
        List<HomeworkRow> todo = rows.stream().filter(r -> r.status().equals("TODO") || r.status().equals("RETURNED")).toList();
        String nearestDue = todo.stream().map(HomeworkRow::dueAt).filter(d -> d != null).sorted().findFirst().orElse(null);
        List<HomeworkRow> recent = rows.stream().sorted(Comparator.comparingInt(StudentView::priority)).limit(3).toList();
        return new Home(upcoming.isEmpty() ? null : lesson(upcoming.get(0), student), subscription(me, student), todo.size(),
                nearestDue, recent, monthAttendance(student));
    }

    @Transactional(readOnly = true)
    public List<LessonDto> schedule(User student) {
        return schedule.notEndedForGroups(groupIds(student), SCHEDULE_DAYS).stream().map(l -> lesson(l, student)).toList();
    }

    @Transactional(readOnly = true)
    public List<HomeworkRow> homeworkRows(User student) {
        return homework.issuedForStudent(student.getId()).stream()
                .map(h -> row(h, submissions.forStudent(h.getId(), student.getId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<HomeworkDetail> homeworkDetail(ApiUser me, User student, long homeworkId) {
        return homework.findForStudent(homeworkId, student.getId()).map(h -> {
            Submission sub = submissions.forStudent(homeworkId, student.getId()).orElse(null);
            HomeworkRow row = row(h, sub);
            boolean hidden = me.role() == Role.PARENT;
            List<PartDto> parts = hidden ? List.of() : h.items().stream()
                    .map(i -> part(i.toPart(), i.getId()))
                    .toList();
            boolean canSubmit = me.role() == Role.STUDENT
                    && (row.status().equals("TODO") || row.status().equals("RETURNED"));
            boolean ownAnswer = me.role() == Role.STUDENT && sub != null && sub.getStatus() != SubmissionStatus.DRAFT;
            List<PartDto> answer = !ownAnswer ? List.of() : sub.currentItems().stream()
                    .map(i -> part(i.toPart(), i.getId()))
                    .toList();
            return new HomeworkDetail(row, parts, hidden, canSubmit, answer, ownAnswer ? sub.getAttempt() : 0);
        });
    }

    @Transactional(readOnly = true)
    public List<MaterialLesson> materials(User student) {
        return materials.lessonsWithMaterials(groupIds(student), MATERIAL_LESSONS).stream()
                .map(l -> new MaterialLesson(l.getId(), l.getStartsAt().toString(), l.getGroup().getName(),
                        materials.counts(l.getId()).published()))
                .toList();
    }

    /** Материалы занятия: только для тех, кто может их видеть , иначе пусто. */
    @Transactional(readOnly = true)
    public Optional<MaterialDetail> materialDetail(ApiUser me, User student, long lessonId) {
        Optional<Lesson> lesson = schedule.find(lessonId)
                .filter(l -> groups.isMember(l.getGroup().getId(), student.getId()));
        if (lesson.isEmpty()) {
            return Optional.empty();
        }
        Lesson l = lesson.get();
        List<PartDto> parts = materials.all(lessonId).stream()
                .filter(LessonMaterial::isPublished)
                .map(m -> part(m.toPart(), m.getId()))
                .toList();
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new MaterialDetail(
                new MaterialLesson(l.getId(), l.getStartsAt().toString(), l.getGroup().getName(), parts.size()), parts));
    }

    private AttendanceDto monthAttendance(User student) {
        Instant from = schedule.today().withDayOfMonth(1).atStartOfDay(schedule.zone()).toInstant();
        List<Attendance> marks = attendance
                .findByStudent_IdAndLesson_StartsAtGreaterThanEqualAndLesson_StartsAtLessThanOrderByLesson_StartsAtAsc(
                        student.getId(), from, Instant.now());
        int present = (int) marks.stream().filter(a -> a.getStatus() == AttendanceStatus.PRESENT).count();
        return new AttendanceDto(present, marks.size());
    }

    private Set<Long> groupIds(User student) {
        return groups.groupsOf(student.getId()).stream().map(StudyGroup::getId).collect(Collectors.toSet());
    }

    private LessonDto lesson(Lesson l, User student) {
        StudyGroup g = l.getGroup();
        String place = g.getLocationType() == null ? null : g.getLocationType().name().toLowerCase();
        Instant now = Instant.now();
        return new LessonDto(l.getId(), l.getStartsAt().toString(), l.getDurationMin(), g.getName(), place,
                g.getMeetingUrl(), g.getAddress(), l.isOngoing(now), sendLog.skipped(l.getId(), List.of(student.getId())),
                l.isUpcoming(now));
    }

    /** Сумму абонемента видят родители и репетитор, ученик — только счётчик . */
    private SubscriptionDto subscription(ApiUser me, User student) {
        Optional<Subscription> current = billing.current(student.getId());
        return current.map(s -> new SubscriptionDto(s.getLessonsUsed(), s.getLessonsTotal(),
                me.role() == Role.STUDENT ? null : s.getAmount(), s.getPaidAt().toString())).orElse(null);
    }

    private static HomeworkRow row(Homework h, Submission s) {
        String status = s == null || s.getStatus() == SubmissionStatus.DRAFT ? "TODO" : s.getStatus().name();
        boolean overdue = h.getDueAt() != null && Instant.now().isAfter(h.getDueAt());
        boolean reviewed = s != null && (s.getStatus() == SubmissionStatus.CHECKED || s.getStatus() == SubmissionStatus.RETURNED);
        return new HomeworkRow(h.getId(), h.getTitle(), h.getGroup().getName(),
                h.getIssuedAt() == null ? null : h.getIssuedAt().toString(),
                h.getDueAt() == null ? null : h.getDueAt().toString(), overdue, h.getMaxScore(), status,
                s != null && s.getStatus() == SubmissionStatus.CHECKED ? s.getScore() : null,
                reviewed ? s.getComment() : null);
    }

    /** Сначала то, что нужно сделать, потом ждущее проверки, потом проверенное. */
    private static int priority(HomeworkRow r) {
        return switch (r.status()) {
            case "RETURNED" -> 0;
            case "TODO" -> 1;
            case "SUBMITTED" -> 2;
            default -> 3;
        };
    }

    /** Часть для показа; id — строки задания, материала или ответа (по нему отдаётся файл). */
    PartDto part(Part p, long id) {
        boolean text = p.kind() == ItemKind.TEXT;
        return new PartDto(p.kind().name(), p.text(), text ? null : id, text ? null : files.name(p.fileId()).orElse(null));
    }

    /** Файл из задания, если этот пользователь может его видеть (ученик — своё ДЗ, репетитор — любое). */
    @Transactional(readOnly = true)
    public Optional<String> homeworkFile(ApiUser me, long itemId) {
        return homeworkItems.findById(itemId)
                .filter(i -> i.getHomework().getStatus() == HomeworkStatus.ISSUED)
                .filter(i -> me.role() == Role.TUTOR || (me.role() == Role.STUDENT && i.getHomework().isAssigned(me.userId())))
                .map(HomeworkItem::toPart)
                .filter(p -> p.kind() != ItemKind.TEXT)
                .map(Part::fileId);
    }

    /** Файл из собственного отправленного ответа ученика. Родителю ответы не показываются. */
    @Transactional(readOnly = true)
    public Optional<String> submissionFile(ApiUser me, long itemId) {
        return submissionItems.findById(itemId)
                .filter(i -> me.role() == Role.STUDENT && i.getSubmission().getStudent().getId() == me.userId())
                .filter(i -> i.getSubmission().getStatus() != SubmissionStatus.DRAFT)
                .map(SubmissionItem::toPart)
                .filter(p -> p.kind() != ItemKind.TEXT)
                .map(Part::fileId);
    }

    /** Файл из материалов, если пользователь может открыть материалы занятия. */
    @Transactional(readOnly = true)
    public Optional<String> materialFile(ApiUser me, long materialId) {
        Optional<User> viewer = users.findById(me.userId());
        return materials.find(materialId)
                .filter(LessonMaterial::isPublished)
                .filter(m -> me.role() == Role.TUTOR || viewer.map(v -> materials.canView(v, m.getLesson())).orElse(false))
                .map(LessonMaterial::toPart)
                .filter(p -> p.kind() != ItemKind.TEXT)
                .map(Part::fileId);
    }
}
