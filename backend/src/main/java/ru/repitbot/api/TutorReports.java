package ru.repitbot.api;

import org.springframework.stereotype.Service;
import ru.repitbot.config.AppProperties;
import ru.repitbot.domain.report.ReportService;
import ru.repitbot.domain.report.WeeklyReport;
import ru.repitbot.domain.schedule.When;
import ru.repitbot.notify.ReportRules;
import ru.repitbot.notify.ReportText;
import ru.repitbot.notify.WeeklyReports;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

/**
 * Экран «Отчёты родителям» (бывший раздел бота): отчёты ближайшей отчётной недели, комментарий к каждому,
 * предпросмотр — ровно то, что получит родитель, и «Отправить сейчас» в день отчёта.
 * Автоматическая рассылка ({@link WeeklyReports#runDue}) работает как раньше; отправленное повторно не уйдёт.
 */
@Service
public class TutorReports {

    /** preview — текст отчёта, каким его увидит родитель; sent — уже отправлен. */
    public record Report(long id, long studentId, String name, String comment, boolean sent, String preview) {
    }

    /**
     * week — «13–19 октября»; sendAt — «сегодня в 18:00»; canSendNow — сегодня день отчёта (или следующий,
     * если рассылка запаздывает) и не всё отправлено; unmarked — не отмеченных занятий недели.
     */
    public record Screen(String week, String sendAt, boolean canSendNow, int unmarked, List<Report> reports) {
    }

    private final ReportService reports;
    private final WeeklyReports weekly;
    private final AppProperties props;
    private final ZoneId zone;

    public TutorReports(ReportService reports, WeeklyReports weekly, AppProperties props) {
        this.reports = reports;
        this.weekly = weekly;
        this.props = props;
        this.zone = props.zone();
    }

    public Screen screen() {
        LocalDate today = LocalDate.now(zone);
        Optional<LocalDate> catchingUp = ReportRules.reportDay(today, props.reportDayOfWeek());
        LocalDate reportDay = catchingUp.orElse(today.with(TemporalAdjusters.next(props.reportDayOfWeek())));
        LocalDate week = ReportRules.weekStart(reportDay);
        // prepare() добавляет отчёты тем, у кого за неделю что-то появилось; уже созданные не трогает.
        reports.prepare(week);
        List<Report> list = reports.forWeek(week).stream().map(this::report).toList();
        boolean allSent = !list.isEmpty() && list.stream().allMatch(Report::sent);
        return new Screen(ReportText.weekRange(week),
                When.day(reportDay, today) + " в " + When.time(props.reportLocalTime()),
                catchingUp.isPresent() && !list.isEmpty() && !allSent,
                weekly.unmarkedOf(week).size(), list);
    }

    public Report comment(long reportId, String comment) {
        return report(reports.setComment(reportId, comment));
    }

    /** «Отправить сейчас» — только в день отчёта: раньше неделя ещё не закончилась. */
    public WeeklyReports.Sent sendNow() {
        LocalDate today = LocalDate.now(zone);
        LocalDate reportDay = ReportRules.reportDay(today, props.reportDayOfWeek())
                .orElseThrow(() -> new IllegalStateException("Отправить сейчас можно в день отчёта"));
        return weekly.send(ReportRules.weekStart(reportDay))
                .orElseThrow(() -> new IllegalStateException("Отчёты этой недели уже отправлены"));
    }

    private Report report(WeeklyReport r) {
        return new Report(r.getId(), r.getStudent().getId(), r.getStudent().displayName(), r.getComment(), r.isSent(),
                weekly.render(r));
    }
}
