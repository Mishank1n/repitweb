package ru.repitbot.domain.homework;

/**
 * Одна часть задания или ответа: текст или файл с подписью.
 *
 * @param kind   тип
 * @param fileId id файла из таблицы files, null для текста
 * @param text   текст сообщения или подпись к файлу, может быть null
 */
public record Part(ItemKind kind, String fileId, String text) {

    /** Лимит длины одного текстового блока. */
    public static final int MAX_TEXT = 4096;

    public Part {
        if (kind == ItemKind.TEXT && (text == null || text.isBlank())) {
            throw new IllegalArgumentException("Пустой текст");
        }
        if (kind != ItemKind.TEXT && (fileId == null || fileId.isBlank())) {
            throw new IllegalArgumentException("Нет файла");
        }
        if (text != null && text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT);
        }
    }

    public static Part text(String text) {
        return new Part(ItemKind.TEXT, null, text);
    }
}
