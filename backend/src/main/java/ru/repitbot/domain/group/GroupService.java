package ru.repitbot.domain.group;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.domain.user.User;

import java.util.List;
import java.util.Optional;

@Service
public class GroupService {

    private final StudyGroupRepository groups;
    private final GroupMemberRepository members;
    private final ParentLinkRepository parentLinks;

    public GroupService(StudyGroupRepository groups, GroupMemberRepository members, ParentLinkRepository parentLinks) {
        this.groups = groups;
        this.members = members;
        this.parentLinks = parentLinks;
    }

    /** Название без лишних пробелов: «  ЕГЭ  английский » → «ЕГЭ английский». */
    static String validName(String rawName) {
        String name = rawName == null ? "" : rawName.strip().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Название не может быть пустым");
        }
        if (name.length() > StudyGroup.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Название длиннее " + StudyGroup.MAX_NAME_LENGTH + " символов");
        }
        return name;
    }

    @Transactional
    public StudyGroup create(String rawName) {
        return groups.save(new StudyGroup(validName(rawName)));
    }

    @Transactional
    public StudyGroup rename(long groupId, String rawName) {
        String name = validName(rawName);
        StudyGroup group = groups.findById(groupId).orElseThrow();
        group.rename(name);
        return group;
    }

    /**
     * Удаляет группу. Состав, расписание, занятия (с журналом напоминаний) и приглашения
     * удаляются каскадом в БД. Ученики и родители остаются.
     */
    @Transactional
    public Optional<StudyGroup> delete(long groupId) {
        Optional<StudyGroup> group = groups.findById(groupId);
        group.ifPresent(groups::delete);
        return group;
    }

    @Transactional(readOnly = true)
    public List<StudyGroup> all() {
        return groups.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Optional<StudyGroup> find(long groupId) {
        return groups.findById(groupId);
    }

    @Transactional(readOnly = true)
    public List<User> students(long groupId) {
        return members.findByGroup_IdOrderByJoinedAtAsc(groupId).stream()
                .map(GroupMember::getStudent)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isMember(long groupId, long studentId) {
        return members.existsByGroup_IdAndStudent_Id(groupId, studentId);
    }

    @Transactional(readOnly = true)
    public long studentCount(long groupId) {
        return members.countByGroup_Id(groupId);
    }

    @Transactional(readOnly = true)
    public List<StudyGroup> groupsOf(long studentId) {
        return members.findByStudent_IdOrderByJoinedAtAsc(studentId).stream()
                .map(GroupMember::getGroup)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<User> parentsOf(long studentId) {
        return parentLinks.findByStudent_Id(studentId).stream()
                .map(ParentLink::getParent)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<User> childrenOf(long parentId) {
        return parentLinks.findByParent_Id(parentId).stream()
                .map(ParentLink::getStudent)
                .toList();
    }

    @Transactional
    public boolean removeStudent(long groupId, long studentId) {
        return members.deleteByGroup_IdAndStudent_Id(groupId, studentId) > 0;
    }

    /** Онлайн-занятия с постоянной ссылкой. Принимаем только https-ссылки без пробелов. */
    @Transactional
    public StudyGroup setOnline(long groupId, String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.strip();
        if (!url.startsWith("https://") || url.length() <= "https://".length() || url.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Ссылка должна начинаться с https:// и не содержать пробелов");
        }
        if (url.length() > StudyGroup.MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Ссылка длиннее " + StudyGroup.MAX_URL_LENGTH + " символов");
        }
        StudyGroup group = groups.findById(groupId).orElseThrow();
        group.setOnline(url);
        return group;
    }

    @Transactional
    public StudyGroup setOffline(long groupId, String rawAddress) {
        String address = rawAddress == null ? "" : rawAddress.strip();
        if (address.isEmpty()) {
            throw new IllegalArgumentException("Адрес не может быть пустым");
        }
        if (address.length() > StudyGroup.MAX_ADDRESS_LENGTH) {
            throw new IllegalArgumentException("Адрес длиннее " + StudyGroup.MAX_ADDRESS_LENGTH + " символов");
        }
        StudyGroup group = groups.findById(groupId).orElseThrow();
        group.setOffline(address);
        return group;
    }

    @Transactional
    public boolean unlinkParent(long parentId, long studentId) {
        return parentLinks.deleteByParent_IdAndStudent_Id(parentId, studentId) > 0;
    }
}
