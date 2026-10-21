package ru.repitbot.domain.billing;

import java.util.ArrayList;
import java.util.List;

/**
 * Отметка ученика на занятии. В callback-данных кодируется одной буквой на ученика: «PCF».
 */
public enum AttendanceStatus {
    PRESENT('P', "✅", "был"),
    ABSENT_CHARGED('C', "❌", "пропуск, списать"),
    ABSENT_FREE('F', "🆓", "пропуск, не списывать");

    private final char code;
    private final String icon;
    private final String label;

    AttendanceStatus(char code, String icon, String label) {
        this.code = code;
        this.icon = icon;
        this.label = label;
    }

    /** Списывается ли занятие из абонемента. */
    public boolean charged() {
        return this != ABSENT_FREE;
    }

    /** Следующая отметка при нажатии: был → пропуск, списать → пропуск, не списывать → был. */
    public AttendanceStatus next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public char code() {
        return code;
    }

    public String icon() {
        return icon;
    }

    public String label() {
        return label;
    }

    /** «PCF» → статусы; длина должна совпасть с числом учеников. */
    public static List<AttendanceStatus> decode(String codes, int expected) {
        if (codes.length() != expected) {
            throw new IllegalArgumentException("Неверное число отметок");
        }
        List<AttendanceStatus> result = new ArrayList<>(codes.length());
        for (char c : codes.toCharArray()) {
            result.add(fromCode(c));
        }
        return result;
    }

    public static String encode(List<AttendanceStatus> statuses) {
        StringBuilder sb = new StringBuilder(statuses.size());
        statuses.forEach(s -> sb.append(s.code));
        return sb.toString();
    }

    /** Коды с переключённой отметкой ученика под номером index. */
    public static String toggle(String codes, int index) {
        if (index < 0 || index >= codes.length()) {
            throw new IllegalArgumentException("Нет такого ученика");
        }
        char next = fromCode(codes.charAt(index)).next().code;
        return codes.substring(0, index) + next + codes.substring(index + 1);
    }

    private static AttendanceStatus fromCode(char c) {
        for (AttendanceStatus s : values()) {
            if (s.code == c) {
                return s;
            }
        }
        throw new IllegalArgumentException("Неизвестная отметка " + c);
    }
}
