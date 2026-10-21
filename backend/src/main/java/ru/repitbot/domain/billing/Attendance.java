package ru.repitbot.domain.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import ru.repitbot.domain.schedule.Lesson;
import ru.repitbot.domain.user.User;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Отметка ученика на занятии. subscription заполнен, если занятие списано из абонемента. */
@Entity
@Table(name = "attendance")
public class Attendance {

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "lesson_id")
        private Long lessonId;

        @Column(name = "student_id")
        private Long studentId;

        protected Key() {
        }

        Key(Long lessonId, Long studentId) {
            this.lessonId = lessonId;
            this.studentId = studentId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(lessonId, k.lessonId) && Objects.equals(studentId, k.studentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lessonId, studentId);
        }
    }

    @EmbeddedId
    private Key id;

    @MapsId("lessonId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @MapsId("studentId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendanceStatus status;

    @ManyToOne
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @Column(name = "marked_at", nullable = false)
    private Instant markedAt = Instant.now();

    protected Attendance() {
    }

    Attendance(Lesson lesson, User student, AttendanceStatus status) {
        this.id = new Key(lesson.getId(), student.getId());
        this.lesson = lesson;
        this.student = student;
        this.status = status;
    }

    void mark(AttendanceStatus status) {
        this.status = status;
        this.markedAt = Instant.now();
    }

    void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    public Lesson getLesson() {
        return lesson;
    }

    public User getStudent() {
        return student;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public Subscription getSubscription() {
        return subscription;
    }
}
