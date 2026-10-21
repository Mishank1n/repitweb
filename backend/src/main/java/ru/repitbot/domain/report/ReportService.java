package ru.repitbot.domain.report;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.config.AppProperties;
import ru.repitbot.domain.billing.Attendance;
import ru.repitbot.domain.billing.AttendanceRepository;
import ru.repitbot.domain.billing.AttendanceStatus;
import ru.repitbot.domain.billing.BillingService;
import ru.repitbot.domain.billing.Subscription;
import ru.repitbot.domain.group.GroupService;
import ru.repitbot.domain.homework.Homework;
import ru.repitbot.domain.homework.HomeworkRepository;
import ru.repitbot.domain.homework.HomeworkStatus;
import ru.repitbot.domain.homework.HomeworkTitles;
import ru.repitbot.domain.homework.Submission;
import ru.repitbot.domain.homework.SubmissionService;
import ru.repitbot.domain.homework.SubmissionStatus;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;
import ru.repitbot.notify.SendLog;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Недельные отчёты родителям: кому, что в отчёте, комментарий репетитора, отметка об отправке.
 * Отчёт получает ученик с согласием, у которого есть родитель с согласием и было что-то за неделю.
 */
@Service
public class ReportService {

    private final WeeklyReportRepository reports;
    private final UserService users;
    private final GroupService groups;
    private final AttendanceRepository attendance;
    private final HomeworkRepository homework;
    private final SubmissionService submissions;
    private final BillingService billing;
    private final SendLog sendLog;
    private final ZoneId zone;

    public ReportService(WeeklyReportRepository reports, UserService users, GroupService groups,
                         AttendanceRepository attendance, HomeworkRepository homework, SubmissionService submissions,
                         BillingService billing, SendLog sendLog, AppProperties props) {
        this.reports = reports;
        this.users = users;
        this.groups = groups;
        this.attendance = attendance;
        this.homework = homework;
        this.submissions = submissions;
        this.billing = billing;
        this.sendLog = sendLog;
        this.zone = props.zone();
    }

    /** Создаёт отчёты недели для всех, кому они нужны; возвращает ещё не отправленные. */
    @Transactional
    public List<WeeklyReport> prepare(LocalDate weekStart) {
        for (User student : users.students()) {
            if (reports.findByStudent_IdAndWeekStart(student.getId(), weekStart).isPresent()) {
                continue;
            }
            if (users.canMessage(student) && hasParentToTell(student) && !data(student, weekStart, null).isEmpty()) {
                reports.save(new WeeklyReport(student, weekStart));
            }
        }
        return unsent(weekStart);
    }

    @Transactional(readOnly = true)
    public List<WeeklyReport> unsent(LocalDate weekStart) {
        return reports.findByWeekStartOrderByIdAsc(weekStart).stream().filter(r -> !r.isSent()).toList();
    }

    @Transactional(readOnly = true)
    public List<WeeklyReport> forWeek(LocalDate weekStart) {
        return reports.findByWeekStartOrderByIdAsc(weekStart);
    }

    @Transactional(readOnly = true)
    public Optional<WeeklyReport> find(long reportId) {
        return reports.findById(reportId);
    }

    @Transactional
    public WeeklyReport setComment(long reportId, String text) {
        WeeklyReport r = reports.findById(reportId).orElseThrow(() -> new NoSuchElementException("Отчёт не найден"));
        r.setComment(text);
        return r;
    }

    @Transactional
    public void markSent(long reportId) {
        reports.findById(reportId).ifPresent(WeeklyReport::markSent);
    }

    @Transactional(readOnly = true)
    public ReportData data(WeeklyReport r) {
        return data(r.getStudent(), r.getWeekStart(), r.getComment());
    }

    private ReportData data(User student, LocalDate weekStart, String comment) {
        Instant from = weekStart.atStartOfDay(zone).toInstant();
        Instant to = weekStart.plusDays(7).atStartOfDay(zone).toInstant();
        List<Long> warners = new ArrayList<>();
        warners.add(student.getId());
        groups.parentsOf(student.getId()).forEach(p -> warners.add(p.getId()));

        List<ReportData.LessonLine> lessons = new ArrayList<>();
        for (Attendance a : attendance
                .findByStudent_IdAndLesson_StartsAtGreaterThanEqualAndLesson_StartsAtLessThanOrderByLesson_StartsAtAsc(
                        student.getId(), from, to)) {
            lessons.add(new ReportData.LessonLine(a.getLesson().getStartsAt().atZone(zone).toLocalDate(),
                    a.getLesson().getGroup().getName(), a.getStatus(),
                    a.getStatus() != AttendanceStatus.PRESENT && sendLog.skipped(a.getLesson().getId(), warners)));
        }

        List<ReportData.HomeworkLine> hw = new ArrayList<>();
        for (Homework h : homework.findActiveForStudent(student.getId(), HomeworkStatus.ISSUED, from, to)) {
            Submission s = submissions.forStudent(h.getId(), student.getId()).orElse(null);
            boolean reviewed = s != null && (s.getStatus() == SubmissionStatus.CHECKED || s.getStatus() == SubmissionStatus.RETURNED);
            hw.add(new ReportData.HomeworkLine(h.getTitle(), HomeworkTitles.studentStatus(h, s), reviewed ? s.getComment() : null));
        }

        String sub = billing.current(student.getId()).map(Subscription::counter).orElse(null);
        return new ReportData(student.displayName(), weekStart, lessons, hw, sub, comment);
    }

    private boolean hasParentToTell(User student) {
        return groups.parentsOf(student.getId()).stream().anyMatch(users::canMessage);
    }
}
