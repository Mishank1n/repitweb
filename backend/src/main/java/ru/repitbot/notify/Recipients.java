package ru.repitbot.notify;

import org.springframework.stereotype.Component;
import ru.repitbot.domain.group.GroupService;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Кому писать об учениках: сами ученики и их родители, только с действующим согласием.
 * Ученик, отозвавший согласие, исключается вместе с упоминанием в сообщениях родителям.
 */
@Component
public class Recipients {

    /** Получатель и имена его детей (для родителя); пусто для ученика. */
    public record Recipient(User user, List<String> children) {
        public boolean isParent() {
            return !children.isEmpty();
        }
    }

    private final GroupService groups;
    private final UserService users;

    public Recipients(GroupService groups, UserService users) {
        this.groups = groups;
        this.users = users;
    }

    public List<Recipient> forGroup(long groupId) {
        return forStudents(groups.students(groupId));
    }

    public List<Recipient> forStudents(Collection<User> students) {
        Map<Long, Recipient> result = new LinkedHashMap<>();
        for (User student : students) {
            if (!users.canMessage(student)) {
                continue;
            }
            result.putIfAbsent(student.getId(), new Recipient(student, new ArrayList<>()));
            for (User parent : groups.parentsOf(student.getId())) {
                if (users.canMessage(parent)) {
                    result.computeIfAbsent(parent.getId(), id -> new Recipient(parent, new ArrayList<>()))
                            .children().add(student.getFirstName());
                }
            }
        }
        return new ArrayList<>(result.values());
    }
}
