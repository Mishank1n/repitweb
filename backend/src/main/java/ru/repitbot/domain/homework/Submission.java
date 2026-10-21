package ru.repitbot.domain.homework;

import jakarta.persistence.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import ru.repitbot.domain.user.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Сдача ДЗ учеником. Одна на ученика и ДЗ; после доработки ответ присылается следующей попыткой. */
@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "homework_id")
    private Homework homework;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    @Column(name = "attempt", nullable = false)
    private int attempt = 1;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "late", nullable = false)
    private boolean late;

    @Column(name = "score")
    private Integer score;

    @Column(name = "comment")
    private String comment;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @Fetch(FetchMode.SELECT)
    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("attempt ASC, position ASC")
    private List<SubmissionItem> items = new ArrayList<>();

    protected Submission() {
    }

    public Submission(Homework homework, User student) {
        this.homework = homework;
        this.student = student;
    }

    /** Части ответа текущей попытки. */
    /** Части текущей попытки вместе с id — для превью фото в Mini App. */
    public List<SubmissionItem> currentItems() {
        return items.stream().filter(i -> i.getAttempt() == attempt).toList();
    }

    public List<Part> currentParts() {
        return items.stream().filter(i -> i.getAttempt() == attempt).map(SubmissionItem::toPart).toList();
    }

    public int addPart(Part part) {
        require(SubmissionStatus.DRAFT);
        int count = currentParts().size();
        if (count >= Homework.MAX_ITEMS) {
            throw new IllegalStateException("Не больше " + Homework.MAX_ITEMS + " частей в одном ответе");
        }
        items.add(new SubmissionItem(this, attempt, count, part));
        return count + 1;
    }

    /** Пересдача после доработки: новая попытка, прошлые ответы сохраняются. */
    public void reopen() {
        require(SubmissionStatus.RETURNED);
        attempt++;
        status = SubmissionStatus.DRAFT;
    }

    /** Отмена черновика: удаляет ответы текущей попытки. Возвращает true, если сдачу можно удалить целиком. */
    public boolean discardDraft() {
        require(SubmissionStatus.DRAFT);
        items.removeIf(i -> i.getAttempt() == attempt);
        if (attempt > 1) {
            attempt--;
            status = SubmissionStatus.RETURNED;
            return false;
        }
        return true;
    }

    public void submit(Instant now) {
        require(SubmissionStatus.DRAFT);
        if (currentParts().isEmpty()) {
            throw new IllegalStateException("Ответ пустой: пришлите фото, файлы или текст");
        }
        status = SubmissionStatus.SUBMITTED;
        submittedAt = now;
        late = homework.getDueAt() != null && now.isAfter(homework.getDueAt());
    }

    public void grade(int score, String comment) {
        require(SubmissionStatus.SUBMITTED);
        if (score < 0 || score > homework.getMaxScore()) {
            throw new IllegalArgumentException("Баллы должны быть от 0 до " + homework.getMaxScore());
        }
        this.score = score;
        this.comment = comment;
        this.status = SubmissionStatus.CHECKED;
        this.checkedAt = Instant.now();
    }

    public void returnForRework(String comment) {
        require(SubmissionStatus.SUBMITTED);
        this.comment = comment;
        this.status = SubmissionStatus.RETURNED;
    }

    private void require(SubmissionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(switch (status) {
                case DRAFT -> "Ответ ещё не отправлен";
                case SUBMITTED -> "Работа уже отправлена и ждёт проверки";
                case CHECKED -> "Работа уже проверена";
                case RETURNED -> "Работа возвращена на доработку";
            });
        }
    }

    public Long getId() {
        return id;
    }

    public Homework getHomework() {
        return homework;
    }

    public User getStudent() {
        return student;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public int getAttempt() {
        return attempt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public boolean isLate() {
        return late;
    }

    public Integer getScore() {
        return score;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
