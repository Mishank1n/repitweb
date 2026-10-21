package ru.repitbot.domain.invite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ru.repitbot.domain.group.StudyGroup;
import ru.repitbot.domain.user.User;

import java.time.Instant;

/** Одноразовая ссылка сайта: /join/CODE (приглашение) или /reset/CODE (новый пароль). */
@Entity
@Table(name = "invites")
public class Invite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private InviteType type;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private StudyGroup group;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private User student;

    /** Для RESET: чей пароль задаётся. */
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @ManyToOne
    @JoinColumn(name = "used_by")
    private User usedBy;

    protected Invite() {
    }

    public static Invite forStudent(String code, StudyGroup group, Instant expiresAt) {
        Invite invite = new Invite();
        invite.code = code;
        invite.type = InviteType.STUDENT;
        invite.group = group;
        invite.expiresAt = expiresAt;
        return invite;
    }

    public static Invite forParent(String code, User student, Instant expiresAt) {
        Invite invite = new Invite();
        invite.code = code;
        invite.type = InviteType.PARENT;
        invite.student = student;
        invite.expiresAt = expiresAt;
        return invite;
    }

    public static Invite forReset(String code, User user, Instant expiresAt) {
        Invite invite = new Invite();
        invite.code = code;
        invite.type = InviteType.RESET;
        invite.user = user;
        invite.expiresAt = expiresAt;
        return invite;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(User by) {
        this.usedAt = Instant.now();
        this.usedBy = by;
    }

    /** Делает неиспользованную ссылку недействительной. Возвращает false, если по ней уже вошли. */
    public boolean revoke() {
        if (usedAt != null) {
            return false;
        }
        Instant now = Instant.now();
        if (expiresAt.isAfter(now)) {
            expiresAt = now;
        }
        return true;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public InviteType getType() {
        return type;
    }

    public StudyGroup getGroup() {
        return group;
    }

    public User getStudent() {
        return student;
    }

    public User getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
