package ru.repitbot.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.repitbot.notify.WeeklyReports;

/** Отчёты родителям за неделю: /api/tutor/reports (только репетитору — проверяет SessionAuth). */
@RestController
@RequestMapping("/api/tutor/reports")
public class TutorReportsController {

    public record CommentBody(String comment) {
    }

    private final TutorReports reports;

    public TutorReportsController(TutorReports reports) {
        this.reports = reports;
    }

    @GetMapping
    public TutorReports.Screen screen() {
        return reports.screen();
    }

    @PostMapping("/{reportId}/comment")
    public TutorReports.Report comment(@PathVariable long reportId, @RequestBody CommentBody body) {
        return reports.comment(reportId, body.comment());
    }

    @PostMapping("/send")
    public WeeklyReports.Sent send() {
        return reports.sendNow();
    }
}
