package ru.repitbot.notify;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;
import ru.repitbot.notify.push.WebPush;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Единственный путь уведомлений к людям: запись в ленту на сайте и web push на устройства человека.
 * Здесь же последняя проверка согласия: отозвавшему согласие ничего не уходит, даже если вызывающий забыл проверить.
 * Push уходит после фиксации транзакции: если действие откатилось, уведомления о нём не будет.
 */
@Component
public class Inbox {

    private static final int MAX_TITLE = 200;
    private static final int MAX_BODY = 4000;
    private static final int FEED_SIZE = 50;

    /**
     * Уведомление.
     *
     * @param title заголовок (с эмодзи), как первая строка push
     * @param body  текст, строки через \n
     * @param url   экран сайта, который откроется по нажатию; null — главная
     */
    public record Notice(String title, String body, String url) {
    }

    public record Item(long id, String title, String body, String url, String createdAt, boolean read) {
    }

    private final JdbcTemplate jdbc;
    private final UserService users;
    private final WebPush push;

    public Inbox(JdbcTemplate jdbc, UserService users, WebPush push) {
        this.jdbc = jdbc;
        this.users = users;
        this.push = push;
    }

    public void send(User user, Notice notice) {
        if (!users.canMessage(user)) {
            return;
        }
        String title = cut(notice.title(), MAX_TITLE);
        String body = cut(notice.body() == null ? "" : notice.body(), MAX_BODY);
        jdbc.update("INSERT INTO notifications (user_id, title, body, url) VALUES (?, ?, ?, ?)",
                user.getId(), title, body, notice.url());
        WebPush.Message message = new WebPush.Message(title, body, notice.url(), unread(user.getId()));
        long userId = user.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.deliver(userId, message);
                }
            });
        } else {
            push.deliver(userId, message);
        }
    }

    public void toTutor(Notice notice) {
        users.findTutor().ifPresent(t -> send(t, notice));
    }

    public List<Item> feed(long userId) {
        return jdbc.query("""
                        SELECT id, title, body, url, created_at, read_at IS NOT NULL
                        FROM notifications WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?""",
                (rs, i) -> new Item(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant().toString(), rs.getBoolean(6)),
                userId, FEED_SIZE);
    }

    public int unread(long userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
                Integer.class, userId);
        return n == null ? 0 : n;
    }

    public void markAllRead(long userId) {
        jdbc.update("UPDATE notifications SET read_at = ? WHERE user_id = ? AND read_at IS NULL",
                Timestamp.from(Instant.now()), userId);
    }

    /** Уведомления старше 90 дней: лента — не архив. */
    public int purgeOld() {
        return jdbc.update("DELETE FROM notifications WHERE created_at < now() - interval '90 days'");
    }

    private static String cut(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int end = Character.isHighSurrogate(s.charAt(max - 2)) ? max - 2 : max - 1;
        return s.substring(0, end) + "…";
    }
}
