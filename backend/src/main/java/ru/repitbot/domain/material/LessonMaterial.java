package ru.repitbot.domain.material;

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
import ru.repitbot.domain.homework.ItemKind;
import ru.repitbot.domain.homework.Part;
import ru.repitbot.domain.schedule.Lesson;

import java.time.Instant;

/** Одна часть материалов занятия. Пока не опубликована, видна только репетитору. */
@Entity
@Table(name = "lesson_materials")
public class LessonMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Column(name = "position", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private ItemKind kind;

    @Column(name = "file_id")
    private String fileId;

    @Column(name = "text")
    private String text;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LessonMaterial() {
    }

    LessonMaterial(Lesson lesson, int position, Part part) {
        this.lesson = lesson;
        this.position = position;
        this.kind = part.kind();
        this.fileId = part.fileId();
        this.text = part.text();
    }

    void publish() {
        this.published = true;
    }

    public Part toPart() {
        return new Part(kind, fileId, text);
    }

    public Long getId() {
        return id;
    }

    public Lesson getLesson() {
        return lesson;
    }

    public int getPosition() {
        return position;
    }

    public boolean isPublished() {
        return published;
    }
}
