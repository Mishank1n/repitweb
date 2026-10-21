package ru.repitbot.domain.invite;

/** Ошибка принятия приглашения; сообщение показывается пользователю как есть. */
public class InviteException extends RuntimeException {

    public InviteException(String userMessage) {
        super(userMessage);
    }
}
