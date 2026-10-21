package ru.repitbot.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.repitbot.api.auth.SessionAuth;
import ru.repitbot.config.AppProperties;
import ru.repitbot.domain.group.GroupService;
import ru.repitbot.domain.user.Role;
import ru.repitbot.domain.user.UserService;

import java.util.List;

/** Кто вошёл: роль, имя, для родителя — дети (только с действующим согласием). */
@RestController
@RequestMapping("/api")
public class MeController {

    public record Child(long id, String firstName, String displayName) {
    }

    /** userId — id пользователя (ученик открывает свои экраны по нему); timezone — пояс репетитора для дат. */
    public record Me(long userId, String login, String role, String firstName, String displayName, String timezone,
                     List<Child> children) {
    }

    private final GroupService groups;
    private final UserService users;
    private final AppProperties props;

    public MeController(GroupService groups, UserService users, AppProperties props) {
        this.groups = groups;
        this.users = users;
        this.props = props;
    }

    @GetMapping("/me")
    public Me me(@RequestAttribute(SessionAuth.ATTR) ApiUser me) {
        List<Child> children = me.role() != Role.PARENT ? List.of()
                : groups.childrenOf(me.userId()).stream()
                .filter(users::canMessage)
                .map(c -> new Child(c.getId(), c.getFirstName(), c.displayName()))
                .toList();
        return new Me(me.userId(), users.findById(me.userId()).orElseThrow().getLogin(), me.role().name(), me.firstName(), me.displayName(), props.zone().getId(), children);
    }
}
