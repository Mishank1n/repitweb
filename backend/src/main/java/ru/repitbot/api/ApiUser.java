package ru.repitbot.api;

import ru.repitbot.domain.user.Role;

/** Кто делает запрос к API: пользователь, найденный по cookie сессии. */
public record ApiUser(long userId, Role role, String firstName, String displayName) {
}
