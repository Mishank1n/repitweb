package ru.repitbot.notify;

import ru.repitbot.domain.billing.AttendanceStatus;
import ru.repitbot.domain.report.ReportData;
import ru.repitbot.domain.schedule.When;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Текст недельного отчёта родителю (обычный текст: сайт показывает его как есть, без разметки). */
public final class ReportText {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", When.RU);

    private ReportText() {
    }

    public static String render(ReportData d) {
        StringBuilder sb = new StringBuilder("👶 ").append(d.studentName()).append("\n\n");

        if (d.lessons().isEmpty()) {
            sb.append("📅 Занятий на этой неделе не было.\n");
        } else {
            long present = d.lessons().stream().filter(l -> l.status() == AttendanceStatus.PRESENT).count();
            sb.append("📅 Занятия: был").append(present == d.lessons().size() ? " на всех (" + present + ")"
                    : " на " + present + " из " + d.lessons().size()).append('\n');
            for (ReportData.LessonLine l : d.lessons()) {
                sb.append("• ").append(When.shortDay(l.date())).append(", «").append(l.groupName()).append("» — ")
                        .append(lessonStatus(l)).append('\n');
            }
        }

        if (!d.homework().isEmpty()) {
            sb.append("\n📚 Домашние задания\n");
            for (ReportData.HomeworkLine h : d.homework()) {
                sb.append("• «").append(h.title()).append("» — ").append(h.status());
                if (h.comment() != null && !h.comment().isBlank()) {
                    sb.append(" 💬 ").append(h.comment());
                }
                sb.append('\n');
            }
        }

        sb.append("\n🎫 ").append(d.subscription() == null ? "Абонемента сейчас нет"
                : "Абонемент: " + d.subscription() + " занятий проведено").append('\n');

        if (d.comment() != null && !d.comment().isBlank()) {
            sb.append("\n💬 Комментарий репетитора:\n").append(d.comment()).append('\n');
        }
        return sb.toString().strip();
    }

    /** «13–19 октября» или «27 октября – 2 ноября». */
    public static String weekRange(LocalDate weekStart) {
        LocalDate end = weekStart.plusDays(6);
        if (weekStart.getMonth() == end.getMonth()) {
            return weekStart.getDayOfMonth() + "–" + DAY_MONTH.format(end);
        }
        return DAY_MONTH.format(weekStart) + " – " + DAY_MONTH.format(end);
    }

    private static String lessonStatus(ReportData.LessonLine l) {
        return switch (l.status()) {
            case PRESENT -> "✅ был";
            case ABSENT_CHARGED, ABSENT_FREE -> "❌ пропуск" + (l.warned() ? " (предупредили заранее)" : "");
        };
    }
}
