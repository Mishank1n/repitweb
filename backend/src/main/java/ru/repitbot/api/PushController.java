package ru.repitbot.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.repitbot.api.auth.SessionAuth;
import ru.repitbot.notify.push.Vapid;
import ru.repitbot.notify.push.WebPush;

import java.util.Map;

/** Web push на этом устройстве: ключ сайта, подписка браузера, отписка, проверочное уведомление. */
@RestController
@RequestMapping("/api/push")
public class PushController {

    /** Подписка в том виде, в каком её отдаёт браузер (PushSubscription.toJSON()). */
    public record SubscribeBody(String endpoint, Keys keys) {
    }

    public record Keys(String p256dh, String auth) {
    }

    public record EndpointBody(String endpoint) {
    }

    private final Vapid vapid;
    private final WebPush push;

    public PushController(Vapid vapid, WebPush push) {
        this.vapid = vapid;
        this.push = push;
    }

    @GetMapping("/key")
    public Map<String, String> key() {
        return Map.of("key", vapid.publicKey());
    }

    @PostMapping("/subscribe")
    public Map<String, Integer> subscribe(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @RequestBody SubscribeBody body,
                                          HttpServletRequest request) {
        if (body.endpoint() == null || body.keys() == null || body.keys().p256dh() == null || body.keys().auth() == null) {
            throw new IllegalArgumentException("Неполная подписка");
        }
        push.subscribe(me.userId(), body.endpoint(), body.keys().p256dh(), body.keys().auth(),
                request.getHeader(HttpHeaders.USER_AGENT));
        return Map.of("devices", push.devices(me.userId()));
    }

    @PostMapping("/unsubscribe")
    public Map<String, Boolean> unsubscribe(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @RequestBody EndpointBody body) {
        if (body.endpoint() != null) {
            push.unsubscribe(me.userId(), body.endpoint());
        }
        return Map.of("ok", true);
    }

    /** Проверка: push на все устройства человека, без записи в ленту. */
    @PostMapping("/test")
    public Map<String, Integer> test(@RequestAttribute(SessionAuth.ATTR) ApiUser me) {
        int devices = push.deliver(me.userId(), new WebPush.Message("🔔 Уведомления работают",
                "Так будут приходить напоминания о занятиях, задания и оценки.", "/", 0));
        return Map.of("devices", devices);
    }
}
