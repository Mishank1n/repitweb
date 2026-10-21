package ru.repitbot.domain.homework;

/** Тип части задания, ответа или материала. Файлы лежат в нашем хранилище (таблица files). */
public enum ItemKind {
    TEXT,
    PHOTO,
    DOCUMENT,
    VIDEO,
    AUDIO
}
