package ru.repitbot.domain.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ru.repitbot.domain.group.StudyGroup;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

/** Регулярное занятие: «еженедельно, пн в 18:00, 60 мин». */
@Entity
@Table(name = "lesson_series")
public class LessonSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id")
    private StudyGroup group;

    /** 1 = понедельник … 7 = воскресенье, как в {@link DayOfWeek}. */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LessonSeries() {
    }

    public LessonSeries(StudyGroup group, DayOfWeek day, LocalTime startTime, int durationMin) {
        this.group = group;
        this.dayOfWeek = day.getValue();
        this.startTime = startTime;
        this.durationMin = durationMin;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public StudyGroup getGroup() {
        return group;
    }

    public DayOfWeek getDay() {
        return DayOfWeek.of(dayOfWeek);
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public int getDurationMin() {
        return durationMin;
    }

    public boolean isActive() {
        return active;
    }
}
