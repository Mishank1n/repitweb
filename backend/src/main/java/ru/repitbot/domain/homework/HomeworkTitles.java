package ru.repitbot.domain.homework;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Разбор ввода репетитора для ДЗ: название, максимум баллов, оценка с комментарием. */
public final class HomeworkTitles {

    public static final int MAX_SCORE_LIMIT = 1000;
    public static final int MAX_COMMENT = 2000;
    private static final int TITLE_LENGTH = 60;
    private static final java.util.regex.Pattern GRADE =
            java.util.regex.Pattern.compile("^\\s*(\\d{1,4})(?:\\s*/\\s*\\d{1,4})?(?:[\\s,.:;—-]+(.*))?$",
                    java.util.regex.Pattern.DOTALL);

    private HomeworkTitles() {
    }

    /** Оценка и необязательный комментарий. */
    public record Grade(int score, String comment) {
    }

    /** Название ДЗ из первой строки текста задания. */
    public static String fromText(String text) {
        String first = text.strip().lines().findFirst().orElse("").strip();
        if (first.length() > TITLE_LENGTH) {
            first = first.substring(0, TITLE_LENGTH - 1).strip() + "…";
        }
        return first;
    }

    /** Название по умолчанию, если в задании нет текста. */
    public static String byDate(LocalDate date) {
        return "ДЗ от " + date.format(DateTimeFormatter.ofPattern("dd.MM"));
    }

    public static int validMaxScore(int value) {
        if (value < 1 || value > MAX_SCORE_LIMIT) {
            throw new IllegalArgumentException("Максимум баллов должен быть от 1 до " + MAX_SCORE_LIMIT);
        }
        return value;
    }

    /** «15» → 15; всё остальное — ошибка с понятным текстом. */
    public static int parseMaxScore(String text) {
        try {
            return validMaxScore(Integer.parseInt(text.strip()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Нужно целое число от 1 до " + MAX_SCORE_LIMIT);
        }
    }

    /** «8», «8 Молодец», «8/10, ошибка в №3» → баллы и комментарий; баллы в пределах 0…max. */
    public static Grade parseGrade(String text, int max) {
        var m = GRADE.matcher(text == null ? "" : text);
        if (!m.matches()) {
            throw new IllegalArgumentException("Начните сообщение с баллов, например: «8 Хорошо, ошибка в №3»");
        }
        int score = Integer.parseInt(m.group(1));
        if (score > max) {
            throw new IllegalArgumentException("Баллов не может быть больше " + max);
        }
        String comment = m.group(2) == null ? null : m.group(2).strip();
        if (comment != null && comment.isEmpty()) {
            comment = null;
        }
        if (comment != null && comment.length() > MAX_COMMENT) {
            throw new IllegalArgumentException("Комментарий длиннее " + MAX_COMMENT + " символов");
        }
        return new Grade(score, comment);
    }

    /** Статус ДЗ глазами ученика и родителя: «⏳ не сдано», «📤 ждёт проверки», «✅ 8/10». */
    public static String studentStatus(Homework hw, Submission s) {
        boolean overdue = hw.getDueAt() != null && Instant.now().isAfter(hw.getDueAt());
        if (s == null || s.getStatus() == SubmissionStatus.DRAFT) {
            return overdue ? "⏳ не сдано, срок прошёл" : "⏳ не сдано";
        }
        return switch (s.getStatus()) {
            case SUBMITTED -> "📤 ждёт проверки";
            case RETURNED -> "🔁 нужно доработать";
            case CHECKED -> "✅ " + s.getScore() + "/" + hw.getMaxScore();
            case DRAFT -> "⏳ не сдано";
        };
    }
}
