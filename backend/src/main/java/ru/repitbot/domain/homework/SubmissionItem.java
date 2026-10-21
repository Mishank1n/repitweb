package ru.repitbot.domain.homework;

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

@Entity
@Table(name = "submission_items")
public class SubmissionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "position", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private ItemKind kind;

    @Column(name = "file_id")
    private String fileId;

    @Column(name = "text")
    private String text;

    protected SubmissionItem() {
    }

    SubmissionItem(Submission submission, int attempt, int position, Part part) {
        this.submission = submission;
        this.attempt = attempt;
        this.position = position;
        this.kind = part.kind();
        this.fileId = part.fileId();
        this.text = part.text();
    }

    public Part toPart() {
        return new Part(kind, fileId, text);
    }

    public Long getId() {
        return id;
    }

    public Submission getSubmission() {
        return submission;
    }

    public int getAttempt() {
        return attempt;
    }
}
