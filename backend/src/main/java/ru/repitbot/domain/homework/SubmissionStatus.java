package ru.repitbot.domain.homework;

public enum SubmissionStatus {
    /** Ученик собирает ответ. */
    DRAFT,
    /** Отправлено, ждёт проверки. */
    SUBMITTED,
    /** Проверено, выставлены баллы. */
    CHECKED,
    /** Возвращено на доработку. */
    RETURNED
}
