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
@Table(name = "parent_links")
public class ParentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "parent_id")
    private User parent;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ParentLink() {
    }

    public ParentLink(User parent, User student) {
        this.parent = parent;
        this.student = student;
    }

    public Long getId() {
        return id;
    }

    public User getParent() {
        return parent;
    }

    public User getStudent() {
        return student;
    }
}
