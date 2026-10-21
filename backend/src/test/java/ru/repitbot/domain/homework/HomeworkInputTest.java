package ru.repitbot.domain.homework;

import org.junit.jupiter.api.Test;
import ru.repitbot.domain.group.StudyGroup;
import ru.repitbot.domain.user.Role;
import ru.repitbot.domain.user.User;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeworkInputTest {

    // ---------- оценка ----------

    @Test
    void parsesScoreWithAndWithoutComment() {
        assertEquals(new HomeworkTitles.Grade(8, null), HomeworkTitles.parseGrade("8", 10));
        assertEquals(new HomeworkTitles.Grade(8, "Молодец"), HomeworkTitles.parseGrade("8 Молодец", 10));
        assertEquals(new HomeworkTitles.Grade(8, "ошибка в №3"), HomeworkTitles.parseGrade("8/10, ошибка в №3", 10));
        assertEquals(new HomeworkTitles.Grade(0, null), HomeworkTitles.parseGrade(" 0 ", 10));
        assertEquals(new HomeworkTitles.Grade(7, "строка 1\nстрока 2"), HomeworkTitles.parseGrade("7 строка 1\nстрока 2", 10));
    }

    @Test
    void rejectsInvalidScores() {
        assertThrows(IllegalArgumentException.class, () -> HomeworkTitles.parseGrade("11", 10));
        assertThrows(IllegalArgumentException.class, () -> HomeworkTitles.parseGrade("-1", 10));
        assertThrows(IllegalArgumentException.class, () -> HomeworkTitles.parseGrade("abc", 10));
        assertThrows(IllegalArgumentException.class, () -> HomeworkTitles.parseGrade("", 10));
    }

    // ---------- свой максимум ----------

    @Test
    void customMaxScore() {
        assertEquals(15, HomeworkTitles.parseMaxScore("15"));
        assertEquals(1000, HomeworkTitles.parseMaxScore(" 1000 "));
        assertThrows(IllegalArgumentException.class, () -> HomeworkTitles.parseMaxScore("0"));
        assertThrows(IllegalArgumentException.class, () -> HomeworkTitles.parseMaxScore("1001"));
        assertThrows(IllegalArgumentException.class, () -> HomeworkTitles.parseMaxScore("abc"));
        assertThrows(IllegalArgumentException.class, () -> HomeworkTitles.parseMaxScore("10.5"));
    }

    // ---------- название ----------

    @Test
    void titleFromFirstLine() {
        assertEquals("Квадратные уравнения", HomeworkTitles.fromText("  Квадратные уравнения\n№1–5 из учебника"));
        String longLine = "Очень длинная строка задания ".repeat(5);
        String title = HomeworkTitles.fromText(longLine);
        assertTrue(title.length() <= 60 && title.endsWith("…"), title);
        assertEquals("ДЗ от 05.01", HomeworkTitles.byDate(LocalDate.of(2026, 1, 5)));
    }

    // ---------- черновик и сдача ----------

    private static User student() {
        return new User("pupil", "hash", Role.STUDENT, "Europe/Moscow");
    }

    @Test
    void issueRequiresContent() {
        Homework hw = new Homework(new StudyGroup("Группа"), List.of(student()));
        assertThrows(IllegalStateException.class, () -> hw.issue("ДЗ от 05.01"));
        hw.addPart(Part.text("Задание"));
        hw.issue("ДЗ от 05.01");
        assertEquals("Задание", hw.getTitle());
        assertThrows(IllegalStateException.class, () -> hw.addPart(Part.text("ещё")), "после выдачи менять нельзя");
    }

    @Test
    void manualTitleOverridesTextTitle() {
        Homework hw = new Homework(new StudyGroup("Группа"), List.of(student()));
        hw.addPart(Part.text("№ 112–118\nи ещё слова"));
        hw.setTitle("   ");
        assertEquals("№ 112–118", hw.getTitle(), "пустое название не затирает взятое из текста");
        hw.setTitle("  Квадратные   уравнения ");
        assertEquals("Квадратные уравнения", hw.getTitle());
        assertThrows(IllegalArgumentException.class, () -> hw.setTitle("я".repeat(Homework.MAX_TITLE + 1)));
    }

    @Test
    void itemLimit() {
        Homework hw = new Homework(new StudyGroup("Группа"), List.of(student()));
        for (int i = 0; i < Homework.MAX_ITEMS; i++) {
            hw.addPart(new Part(ItemKind.PHOTO, "file" + i, null));
        }
        assertThrows(IllegalStateException.class, () -> hw.addPart(Part.text("лишнее")));
    }

    @Test
    void submissionFlowWithReworkAndLateMark() {
        Homework hw = new Homework(new StudyGroup("Группа"), List.of(student()));
        hw.addPart(Part.text("Задание"));
        hw.setMaxScore(10);
        hw.setDueAt(Instant.now().plus(Duration.ofDays(1)));
        hw.issue("ДЗ");

        Submission s = new Submission(hw, student());
        assertThrows(IllegalStateException.class, () -> s.submit(Instant.now()), "пустой ответ не отправить");
        s.addPart(new Part(ItemKind.PHOTO, "photo1", null));
        s.submit(Instant.now());
        assertFalse(s.isLate());

        s.returnForRework("Перерешай №2");
        s.reopen();
        assertEquals(2, s.getAttempt());
        assertTrue(s.currentParts().isEmpty(), "новая попытка начинается с пустого ответа");
        s.addPart(Part.text("Исправил"));
        s.submit(hw.getDueAt().plus(Duration.ofHours(1)));
        assertTrue(s.isLate());

        assertThrows(IllegalArgumentException.class, () -> s.grade(11, null));
        s.grade(9, null);
        assertEquals(SubmissionStatus.CHECKED, s.getStatus());
        assertNull(s.getComment());
    }
}
