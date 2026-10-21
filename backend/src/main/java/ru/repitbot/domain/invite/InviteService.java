package ru.repitbot.domain.invite;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.config.AppProperties;
import ru.repitbot.domain.group.GroupMember;
import ru.repitbot.domain.group.GroupMemberRepository;
import ru.repitbot.domain.group.ParentLink;
import ru.repitbot.domain.group.ParentLinkRepository;
import ru.repitbot.domain.group.StudyGroup;
import ru.repitbot.domain.group.StudyGroupRepository;
import ru.repitbot.domain.user.Role;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserRepository;
import ru.repitbot.domain.user.UserService;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class InviteService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 18 случайных байт = 24 символа base64url: угадать перебором нереально. */
    private static final int CODE_BYTES = 18;
    /** Ссылка на новый пароль живёт сутки: она даёт вход в чужой аккаунт. */
    static final Duration RESET_TTL = Duration.ofHours(24);

    private final InviteRepository invites;
    private final UserRepository users;
    private final UserService userService;
    private final StudyGroupRepository groups;
    private final GroupMemberRepository members;
    private final ParentLinkRepository parentLinks;
    private final AppProperties props;

    public InviteService(InviteRepository invites, UserRepository users, UserService userService,
                         StudyGroupRepository groups, GroupMemberRepository members, ParentLinkRepository parentLinks,
                         AppProperties props) {
        this.invites = invites;
        this.users = users;
        this.userService = userService;
        this.groups = groups;
        this.members = members;
        this.parentLinks = parentLinks;
        this.props = props;
    }

    /**
     * Итог принятия приглашения: кто вступил и куда.
     * Для STUDENT заполнены groupId/groupName, для PARENT — studentId/studentName.
     */
    public record Accepted(User user, InviteType type, Long groupId, String groupName, Long studentId, String studentName) {
    }

    /** Данные нового аккаунта из формы приглашения. */
    public record NewAccount(String login, String password, String firstName, String lastName) {
    }

    public enum RevokeResult { REVOKED, ALREADY_USED, NOT_FOUND }

    /** Действующее приглашение для показа текста согласия. */
    public record Pending(String code, InviteType type, String groupName, String studentName) {
    }

    static String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String link(Invite invite) {
        return props.site() + (invite.getType() == InviteType.RESET ? "/reset/" : "/join/") + invite.getCode();
    }

    @Transactional
    public Invite createForStudent(long groupId) {
        StudyGroup group = groups.findById(groupId).orElseThrow();
        return invites.save(Invite.forStudent(generateCode(), group, expiry()));
    }

    @Transactional
    public Invite createForParent(long studentId) {
        User student = users.findById(studentId)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow();
        return invites.save(Invite.forParent(generateCode(), student, expiry()));
    }

    /** Ссылка на новый пароль для ученика или родителя (репетитор свой пароль меняет в настройках). */
    @Transactional
    public Invite createReset(long userId) {
        User user = users.findById(userId)
                .filter(u -> u.getRole() != Role.TUTOR)
                .orElseThrow();
        return invites.save(Invite.forReset(generateCode(), user, Instant.now().plus(RESET_TTL)));
    }

    @Transactional(readOnly = true)
    public List<Invite> activeForGroup(long groupId) {
        return invites.findByGroup_IdAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(groupId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<Invite> activeParentInvites(long studentId) {
        return invites.findByStudent_IdAndTypeAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(studentId, InviteType.PARENT,
                Instant.now());
    }

    @Transactional(readOnly = true)
    public Optional<Pending> findPending(String code) {
        return usable(code)
                .filter(i -> i.getType() != InviteType.RESET)
                .map(i -> new Pending(i.getCode(), i.getType(),
                        i.getGroup() == null ? null : i.getGroup().getName(),
                        i.getStudent() == null ? null : i.getStudent().displayName()));
    }

    /** Чей пароль задаёт действующая ссылка /reset/CODE. */
    @Transactional(readOnly = true)
    public Optional<User> findReset(String code) {
        return usable(code)
                .filter(i -> i.getType() == InviteType.RESET)
                .map(Invite::getUser);
    }

    /**
     * Приглашение с новым аккаунтом: создаёт пользователя с логином и паролем, фиксирует согласие
     * и добавляет в группу или привязывает к ребёнку. Ошибка в форме (логин занят…) не тратит ссылку.
     */
    @Transactional
    public Accepted acceptNew(String code, NewAccount account) {
        Invite invite = lockUsable(code);
        User user = userService.create(invite.getType().role(), account.login(), account.password(),
                account.firstName(), account.lastName());
        return link(invite, user);
    }

    /** Приглашение для уже вошедшего: второй ребёнок родителя, вторая группа ученика. */
    @Transactional
    public Accepted acceptExisting(String code, long userId) {
        Invite invite = lockUsable(code);
        User user = users.findById(userId).orElseThrow();
        Role role = invite.getType().role();
        if (user.getRole() != role) {
            throw new InviteException("Вы вошли как " + user.getRole().title() + ", а ссылка — для роли «"
                    + role.title() + "». Выйдите из аккаунта и откройте ссылку снова.");
        }
        return link(invite, user);
    }

    /** Новый пароль по ссылке: ссылка одноразовая. Сессии пользователя закрывает вызывающий. */
    @Transactional
    public User useReset(String code, String password) {
        Invite invite = lockUsable(code);
        if (invite.getType() != InviteType.RESET) {
            throw new InviteException("Ссылка недействительна");
        }
        User user = invite.getUser();
        userService.setPassword(user.getId(), password);
        invite.markUsed(user);
        return user;
    }

    @Transactional
    public RevokeResult revoke(long inviteId) {
        return invites.findById(inviteId)
                .map(i -> i.revoke() ? RevokeResult.REVOKED : RevokeResult.ALREADY_USED)
                .orElse(RevokeResult.NOT_FOUND);
    }

    private Accepted link(Invite invite, User user) {
        user.giveConsent();
        invite.markUsed(user);
        if (invite.getType() == InviteType.STUDENT) {
            StudyGroup group = invite.getGroup();
            if (!members.existsByGroup_IdAndStudent_Id(group.getId(), user.getId())) {
                members.save(new GroupMember(group, user));
            }
            return new Accepted(user, InviteType.STUDENT, group.getId(), group.getName(), null, null);
        }
        User student = invite.getStudent();
        if (!parentLinks.existsByParent_IdAndStudent_Id(user.getId(), student.getId())) {
            parentLinks.save(new ParentLink(user, student));
        }
        return new Accepted(user, InviteType.PARENT, null, null, student.getId(), student.displayName());
    }

    private Optional<Invite> usable(String code) {
        return code == null ? Optional.empty() : invites.findByCode(code).filter(i -> i.isUsable(Instant.now()));
    }

    /** Блокировка строки: одну ссылку не примут дважды одновременно. */
    private Invite lockUsable(String code) {
        return invites.findByCodeForUpdate(code)
                .filter(i -> i.isUsable(Instant.now()))
                .orElseThrow(() -> new InviteException("Ссылка уже использована или устарела. Попросите у репетитора новую."));
    }

    private Instant expiry() {
        return Instant.now().plus(Duration.ofDays(props.inviteTtlDays()));
    }
}
