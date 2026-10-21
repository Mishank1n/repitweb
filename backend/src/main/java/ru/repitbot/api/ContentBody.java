package ru.repitbot.api;

import ru.repitbot.domain.file.FileService;
import ru.repitbot.domain.homework.Homework;
import ru.repitbot.domain.homework.ItemKind;
import ru.repitbot.domain.homework.Part;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Содержимое из формы сайта — задание, материалы или ответ ученика: текст и id заранее загруженных файлов.
 * Прикрепить можно только свои файлы: чужой id (подсмотренный или подобранный) — ошибка, а не доступ к файлу.
 */
public record ContentBody(String text, List<String> files) {

    /** Части в порядке формы: сначала текст, потом файлы. */
    public List<Part> parts(FileService store, long userId) {
        List<Part> parts = new ArrayList<>();
        String t = text == null ? "" : text.strip();
        if (t.length() > Part.MAX_TEXT) {
            throw new IllegalArgumentException("Текст длиннее " + Part.MAX_TEXT + " символов — разбейте его или приложите файлом");
        }
        if (!t.isEmpty()) {
            parts.add(Part.text(t));
        }
        for (String id : new LinkedHashSet<>(files == null ? List.<String>of() : files)) {
            ItemKind kind = store.ownKind(id, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Файл не найден — загрузите его ещё раз"));
            parts.add(new Part(kind, id, null));
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Добавьте текст или файлы");
        }
        if (parts.size() > Homework.MAX_ITEMS) {
            throw new IllegalArgumentException("Не больше " + (Homework.MAX_ITEMS - 1) + " файлов за раз");
        }
        return parts;
    }
}
