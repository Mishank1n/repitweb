package ru.repitbot.domain.report;

import ru.repitbot.domain.billing.AttendanceStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Всё, что входит в недельный отчёт, без сущностей и БД: текст строится чистой функцией ru.repitbot.notify.ReportText.
 *
 * @param subscription счётчик открытого абонемента «5/8» или null, если абонемента нет
 * @param comment      комментарий репетитора или null
 */
public record ReportData(String studentName, LocalDate weekStart, List<LessonLine> lessons,
                         List<HomeworkLine> homework, String subscription, String comment) {

    /** @param warned ученик или родитель заранее нажал «Не смогу прийти» */
    public record LessonLine(LocalDate date, String groupName, AttendanceStatus status, boolean warned) {
    }

    /** @param comment комментарий репетитора к работе или null */
    public record HomeworkLine(String title, String status, String comment) {
    }

    /** За неделю не было ни отмеченных занятий, ни ДЗ — отчёт не нужен. */
    public boolean isEmpty() {
        return lessons.isEmpty() && homework.isEmpty();
    }
}
