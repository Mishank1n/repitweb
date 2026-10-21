package ru.repitbot.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.repitbot.api.auth.SessionAuth;
import ru.repitbot.notify.Inbox;

import java.util.List;
import java.util.Map;

/** Лента уведомлений (🔔): последние 50 и счётчик непрочитанных. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    public record Feed(int unread, List<Inbox.Item> items) {
    }

    private final Inbox inbox;

    public NotificationController(Inbox inbox) {
        this.inbox = inbox;
    }

    @GetMapping
    public Feed feed(@RequestAttribute(SessionAuth.ATTR) ApiUser me) {
        return new Feed(inbox.unread(me.userId()), inbox.feed(me.userId()));
    }

    @PostMapping("/read")
    public Map<String, Boolean> read(@RequestAttribute(SessionAuth.ATTR) ApiUser me) {
        inbox.markAllRead(me.userId());
        return Map.of("ok", true);
    }
}
