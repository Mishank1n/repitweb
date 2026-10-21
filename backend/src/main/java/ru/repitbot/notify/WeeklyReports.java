package ru.repitbot.notify;

import org.springframework.stereotype.Component;
import ru.repitbot.config.AppProperties;
import ru.repitbot.domain.report.ReportData;
import ru.repitbot.domain.report.ReportService;
import ru.repitbot.domain.report.WeeklyReport;
import ru.repitbot.domain.schedule.Lesson;
import ru.repitbot.domain.schedule.ScheduleService;
import ru.repitbot.domain.schedule.When;
import ru.repitbot.notify.Inbox.Notice;
import ru.repitbot.notify.Recipients.Recipient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Недельные отчёты родителям: за час до отправки — предложение репетитору дописать комментарии,
 * затем рассылка. Каждое из двух действий выполняется один раз за неделю (daily_job_log),
 * каждый отчёт отправляется один раз (weekly_reports.sent_at).
 */
@Component
public class WeeklyReports {

    static final String PROMPT = "REPORT_PROMPT";
    static final String SEND = "REPORT_SEND";
    private static final String REPORTS = "/reports";
    /** Отчёт родителю целиком в тексте уведомления: открывается лента, где он и лежит. */
    private static final String FEED = "/feed";

    /** Итог рассылки. */
    public record Sent(int parents, int students) {
    }

    private final ReportService reports;
    private final ScheduleService schedule;
    private final Recipients recipients;
    private final SendLog sendLog;
    private final Inbox inbox;
    private final AppProperties props;
    private final ZoneId zone;

    public WeeklyReports(ReportService reports, ScheduleService schedule, Recipients recipients, SendLog sendLog,
                         Inbox inbox, AppProperties props) {
        this.reports = reports;
        this.schedule = schedule;
        this.recipients = recipients;
        this.sendLog = sendLog;
        this.inbox = inbox;
        this.props = props;
        this.zone = props.zone();
    }

    public void runDue() {
        LocalDateTime now = LocalDateTime.now(zone);
        Optional<LocalDate> day = ReportRules.reportDay(now.toLocalDate(), props.reportDayOfWeek());
        if (day.isEmpty()) {
            return;
        }
        LocalDate week = ReportRules.weekStart(day.get());
        if (ReportRules.promptDue(now, day.get(), props.reportLocalTime()) && sendLog.claimDaily(PROMPT, week)) {
            prompt(week, ReportRules.sendAt(day.get(), props.reportLocalTime(), now));
        }
        Optional<LocalDateTime> promptAt = sendLog.ranAt(PROMPT, week).map(i -> LocalDateTime.ofInstant(i, zone));
        if (promptAt.isPresent() && ReportRules.sendDue(now, day.get(), props.reportLocalTime(), promptAt.get())) {
            send(week);
        }
    }

    /** Репетитору: какие отчёты уйдут и сколько занятий не отмечено; комментарии — на экране отчётов. */
    void prompt(LocalDate week, LocalDateTime sendAt) {
        List<WeeklyReport> list = reports.prepare(week);
        List<Lesson> unmarked = unmarkedOf(week);
        if (list.isEmpty() && unmarked.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (list.isEmpty()) {
            sb.append("Пока отправлять нечего: за неделю нет отмеченных занятий и ДЗ.");
        } else {
            sb.append("Отправлю в ").append(When.time(sendAt.toLocalTime())).append(", учеников: ").append(list.size())
                    .append(".\nМожно добавить комментарий к каждому — он будет в конце отчёта. Без комментария отчёт тоже уйдёт.");
        }
        if (!unmarked.isEmpty()) {
            sb.append("\n\n⚠️ Не отмечено занятий за неделю: ").append(unmarked.size())
                    .append(". Пока посещаемость не отмечена, этих занятий нет в отчётах.");
        }
        inbox.toTutor(new Notice("📊 Отчёты родителям за неделю " + ReportText.weekRange(week), sb.toString(), REPORTS));
    }

    /** Отправить отчёты недели родителям; пусто, если они уже отправлены. */
    public Optional<Sent> send(LocalDate week) {
        if (!sendLog.claimDaily(SEND, week)) {
            return Optional.empty();
        }
        // Повторная подготовка: после предложения репетитор мог отметить занятия, и отчёт появился у новых учеников.
        int parents = 0;
        List<WeeklyReport> sent = new ArrayList<>();
        for (WeeklyReport r : reports.prepare(week)) {
            ReportData data = reports.data(r);
            List<Recipient> toParents = recipients.forStudents(List.of(r.getStudent())).stream()
                    .filter(Recipient::isParent).toList();
            if (!data.isEmpty() && !toParents.isEmpty()) {
                Notice notice = new Notice("📊 Отчёт за неделю " + ReportText.weekRange(week) + " · " + data.studentName(),
                        ReportText.render(data), FEED);
                toParents.forEach(p -> inbox.send(p.user(), notice));
                parents += toParents.size();
                sent.add(r);
            }
            reports.markSent(r.getId());
        }
        if (!sent.isEmpty()) {
            inbox.toTutor(new Notice("📊 Отчёты за неделю " + ReportText.weekRange(week) + " отправлены",
                    "Родителей: " + parents + ", учеников: " + sent.size() + ".", REPORTS));
        }
        return Optional.of(new Sent(parents, sent.size()));
    }

    /** Отчёт в том виде, в каком его увидят родители (для предпросмотра). */
    public String render(WeeklyReport r) {
        return ReportText.render(reports.data(r));
    }

    /** Не отмеченные занятия отчётной недели: пока они не отмечены, их нет в отчётах. */
    public List<Lesson> unmarkedOf(LocalDate week) {
        return schedule.unmarked(7).stream()
                .filter(l -> !l.getStartsAt().atZone(zone).toLocalDate().isBefore(week))
                .toList();
    }
}
