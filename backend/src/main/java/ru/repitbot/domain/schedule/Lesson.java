package ru.repitbot.domain.schedule;

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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "lessons")
public class Lesson {

    /** Самое длинное занятие (CHECK в V2): с таким запасом ищем начавшиеся, но не закончившиеся. */
    public static final Duration MAX_DURATION = Duration.ofMinutes(300);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id")
    private StudyGroup group;

    @ManyToOne
    @JoinColumn(name = "series_id")
    private LessonSeries series;

    /** Исходная дата в серии; null для разовых занятий. */
    @Column(name = "series_date")
    private LocalDate seriesDate;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LessonStatus status = LessonStatus.SCHEDULED;

    /** Когда время было назначено в последний раз: от этого зависит, нужно ли напоминание за 24 ч. */
    @Column(name = "time_set_at", nullable = false)
    private Instant timeSetAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Lesson() {
    }

    public static Lesson oneOff(StudyGroup group, Instant startsAt, int durationMin) {
        Lesson lesson = new Lesson();
        lesson.group = group;
        lesson.startsAt = startsAt;
        lesson.durationMin = durationMin;
        return lesson;
    }

    public static Lesson fromSeries(LessonSeries series, LocalDate date, Instant startsAt) {
        Lesson lesson = oneOff(series.getGroup(), startsAt, series.getDurationMin());
        lesson.series = series;
        lesson.seriesDate = date;
        return lesson;
    }

    public void reschedule(Instant newStart) {
        this.startsAt = newStart;
        this.timeSetAt = Instant.now();
    }

    public void cancel() {
        this.status = LessonStatus.CANCELLED;
    }

    /** Посещаемость отмечена. */
    public void markDone() {
        this.status = LessonStatus.DONE;
    }

    public boolean isUpcoming(Instant now) {
        return status == LessonStatus.SCHEDULED && startsAt.isAfter(now);
    }

    public Instant endsAt() {
        return startsAt.plus(Duration.ofMinutes(durationMin));
    }

    /** Уже началось, но ещё не закончилось. */
    public boolean isOngoing(Instant now) {
        return status == LessonStatus.SCHEDULED && !startsAt.isAfter(now) && now.isBefore(endsAt());
    }

    public boolean hasEnded(Instant now) {
        return !now.isBefore(endsAt());
    }

    public Long getId() {
        return id;
    }

    public StudyGroup getGroup() {
        return group;
    }

    public LessonSeries getSeries() {
        return series;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getDurationMin() {
        return durationMin;
    }

    public LessonStatus getStatus() {
        return status;
    }

    public Instant getTimeSetAt() {
        return timeSetAt;
    }
}
