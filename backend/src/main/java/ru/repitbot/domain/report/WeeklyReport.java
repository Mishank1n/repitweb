package ru.repitbot.domain.report;

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
import java.time.LocalDate;

/** Недельный отчёт родителям об ученике: комментарий репетитора и отметка об отправке. */
@Entity
@Table(name = "weekly_reports")
public class WeeklyReport {

    public static final int MAX_COMMENT = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "comment")
    private String comment;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected WeeklyReport() {
    }

    WeeklyReport(User student, LocalDate weekStart) {
        this.student = student;
        this.weekStart = weekStart;
    }

    void setComment(String raw) {
        if (sentAt != null) {
            throw new IllegalStateException("Отчёт уже отправлен");
        }
        if (raw == null || raw.isBlank()) {
            comment = null;
            return;
        }
        String text = raw.strip();
        if (text.length() > MAX_COMMENT) {
            throw new IllegalArgumentException("Комментарий длиннее " + MAX_COMMENT + " символов");
        }
        comment = text;
    }

    void markSent() {
        sentAt = Instant.now();
    }

    public boolean isSent() {
        return sentAt != null;
    }

    public Long getId() {
        return id;
    }

    public User getStudent() {
        return student;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public String getComment() {
        return comment;
    }
}
