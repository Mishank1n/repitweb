package ru.repitbot.domain.user;

public enum Role {
    TUTOR,
    STUDENT,
    PARENT;

    public String title() {
        return switch (this) {
            case TUTOR -> "репетитор";
            case STUDENT -> "ученик";
            case PARENT -> "родитель";
        };
    }
}
