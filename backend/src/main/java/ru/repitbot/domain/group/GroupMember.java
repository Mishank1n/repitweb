package ru.repitbot.domain.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ru.repitbot.domain.user.User;

import java.time.Instant;

@Entity
@Table(name = "group_members")
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id")
    private StudyGroup group;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    protected GroupMember() {
    }

    public GroupMember(StudyGroup group, User student) {
        this.group = group;
        this.student = student;
    }

    public Long getId() {
        return id;
    }

    public StudyGroup getGroup() {
        return group;
    }

    public User getStudent() {
        return student;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
