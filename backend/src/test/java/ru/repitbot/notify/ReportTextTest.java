package ru.repitbot.notify;

import org.junit.jupiter.api.Test;
import ru.repitbot.domain.billing.AttendanceStatus;
import ru.repitbot.domain.report.ReportData;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportTextTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);

    private static ReportData full(String subscription, String comment) {
        return new ReportData("Иван <Петров>", MONDAY,
                List.of(new ReportData.LessonLine(MONDAY, "ЕГЭ", AttendanceStatus.PRESENT, false),
                        new ReportData.LessonLine(MONDAY.plusDays(2), "ЕГЭ", AttendanceStatus.ABSENT_FREE, true),
                        new ReportData.LessonLine(MONDAY.plusDays(4), "ЕГЭ", AttendanceStatus.ABSENT_CHARGED, false)),
                List.of(new ReportData.HomeworkLine("Квадратные уравнения", "✅ 8/10", "Хорошо & быстро"),
                        new ReportData.HomeworkLine("Логарифмы", "📤 ждёт проверки", null)),
                subscription, comment);
    }

    @Test
    void fullReport() {
        String text = ReportText.render(full("5/8", "Молодец, <b>так держать</b>"));
        assertTrue(text.startsWith("👶 Иван <Петров>"), "текст как есть: сайт не разбирает разметку");
        assertTrue(text.contains("был на 1 из 3"));
        assertTrue(text.contains("❌ пропуск (предупредили заранее)"));
        assertTrue(text.contains("«Квадратные уравнения» — ✅ 8/10 💬 Хорошо & быстро"));
        assertTrue(text.contains("Абонемент: 5/8 занятий проведено"));
        assertTrue(text.contains("Молодец, <b>так держать</b>"));
    }

    @Test
    void withoutSubscriptionAndComment() {
        String text = ReportText.render(full(null, null));
        assertTrue(text.contains("Абонемента сейчас нет"));
        assertFalse(text.contains("Комментарий репетитора"));
    }

    @Test
    void unwarnedAbsenceHasNoNote() {
        String text = ReportText.render(full("1/8", null));
        assertTrue(text.contains("— ❌ пропуск\n"));
    }

    @Test
    void weekInsideMonth() {
        assertEquals("12–18 октября", ReportText.weekRange(MONDAY));
    }

    @Test
    void weekAcrossMonths() {
        assertEquals("27 октября – 2 ноября", ReportText.weekRange(LocalDate.of(2026, 10, 27)));
    }

    @Test
    void emptyWeek() {
        assertTrue(new ReportData("Иван", MONDAY, List.of(), List.of(), null, null).isEmpty());
    }
}
