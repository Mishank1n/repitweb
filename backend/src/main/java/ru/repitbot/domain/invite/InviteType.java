package ru.repitbot.domain.invite;

import ru.repitbot.domain.user.Role;

public enum InviteType {
    /** Ученик вступает в группу. */
    STUDENT,
    /** Родитель привязывается к ученику. */
    PARENT,
    /** Новый пароль для забывшего (ссылку выдаёт репетитор). */
    RESET;

    /** Кем станет принявший приглашение (только для STUDENT и PARENT). */
    public Role role() {
        return switch (this) {
            case STUDENT -> Role.STUDENT;
            case PARENT -> Role.PARENT;
            case RESET -> throw new IllegalStateException("У ссылки на новый пароль нет роли");
        };
    }
}
