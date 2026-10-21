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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import ru.repitbot.domain.group.StudyGroup;
import ru.repitbot.domain.user.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "homework")
public class Homework {

    public static final int MAX_ITEMS = 20;
    public static final int MAX_TITLE = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id")
    private StudyGroup group;

    @Column(name = "title", nullable = false, length = MAX_TITLE)
    private String title = "";

    @Column(name = "max_score", nullable = false)
    private int maxScore = 10;

    @Column(name = "due_at")
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private HomeworkStatus status = HomeworkStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "issued_at")
    private Instant issuedAt;

    // Коллекции маленькие (до 20 вложений, состав одной группы), поэтому грузим сразу.
    @Fetch(FetchMode.SELECT)
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "homework_assignees",
            joinColumns = @JoinColumn(name = "homework_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id"))
    private Set<User> assignees = new HashSet<>();

    @Fetch(FetchMode.SELECT)
    @OneToMany(mappedBy = "homework", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<HomeworkItem> items = new ArrayList<>();

    protected Homework() {
    }

    public Homework(StudyGroup group, Collection<User> assignees) {
        this.group = group;
        this.assignees.addAll(assignees);
    }

    /** Добавляет часть задания; возвращает число частей. */
    public int addPart(Part part) {
        requireDraft();
        if (items.size() >= MAX_ITEMS) {
            throw new IllegalStateException("Не больше " + MAX_ITEMS + " частей в одном задании");
        }
        items.add(new HomeworkItem(this, items.size(), part));
        if (title.isBlank() && part.text() != null) {
            title = HomeworkTitles.fromText(part.text());
        }
        return items.size();
    }

    public void toggleAssignee(User student) {
        requireDraft();
        if (!assignees.removeIf(u -> u.getId().equals(student.getId()))) {
            assignees.add(student);
        }
    }

    public void setAssignees(Collection<User> students) {
        requireDraft();
        assignees.clear();
        assignees.addAll(students);
    }

    /** Название вручную; пустое — из первой строки текста или по дате выдачи. */
    public void setTitle(String value) {
        requireDraft();
        String t = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (t.length() > MAX_TITLE) {
            throw new IllegalArgumentException("Название длиннее " + MAX_TITLE + " символов");
        }
        if (!t.isEmpty()) {
            title = t;
        }
    }

    public void setMaxScore(int maxScore) {
        requireDraft();
        this.maxScore = HomeworkTitles.validMaxScore(maxScore);
    }

    public void setDueAt(Instant dueAt) {
        requireDraft();
        this.dueAt = dueAt;
    }

    public void issue(String fallbackTitle) {
        requireDraft();
        if (items.isEmpty()) {
            throw new IllegalStateException("Задание пустое: пришлите текст или файлы");
        }
        if (assignees.isEmpty()) {
            throw new IllegalStateException("Не выбран ни один ученик");
        }
        if (title.isBlank()) {
            title = fallbackTitle;
        }
        status = HomeworkStatus.ISSUED;
        issuedAt = Instant.now();
    }

    public boolean isAssigned(long studentId) {
        return assignees.stream().anyMatch(u -> u.getId() == studentId);
    }

    public List<Part> parts() {
        return items.stream().map(HomeworkItem::toPart).toList();
    }

    /** Части вместе с id — для превью фото в Mini App. */
    public List<HomeworkItem> items() {
        return List.copyOf(items);
    }

    private void requireDraft() {
        if (status != HomeworkStatus.DRAFT) {
            throw new IllegalStateException("ДЗ уже выдано");
        }
    }

    public Long getId() {
        return id;
    }

    public StudyGroup getGroup() {
        return group;
    }

    public String getTitle() {
        return title;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public HomeworkStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Set<User> getAssignees() {
        return assignees;
    }

    public int itemCount() {
        return items.size();
    }
}
