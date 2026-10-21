package ru.repitbot.domain.schedule;

public enum LessonStatus {
    SCHEDULED,
    CANCELLED,
    /** Проведено: посещаемость отмечена, занятия списаны из абонементов. */
    DONE
}
