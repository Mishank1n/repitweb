package ru.repitbot.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.domain.file.FileService;
import ru.repitbot.domain.homework.Homework;
import ru.repitbot.domain.homework.HomeworkService;
import ru.repitbot.domain.homework.HomeworkStatus;
import ru.repitbot.domain.homework.Part;
import ru.repitbot.domain.homework.Submission;
import ru.repitbot.domain.homework.SubmissionService;
import ru.repitbot.domain.homework.SubmissionStatus;
import ru.repitbot.domain.material.MaterialService;
import ru.repitbot.domain.schedule.ScheduleInput;
import ru.repitbot.domain.schedule.ScheduleService;
import ru.repitbot.domain.user.User;
import ru.repitbot.notify.HomeworkNotifier;
import ru.repitbot.notify.MaterialsNotifier;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ДЗ и материалы глазами репетитора. В боте задание собиралось по шагам (черновик, части, «Выдать»),
 * на сайте всё это — одна форма: черновик создаётся и выдаётся в одной транзакции, ошибка ничего не оставляет.
 * Ученики и родители получают те же уведомления. Доступ — только репетитору (/api/tutor/**).
 */
@Service
public class TutorContent {

    /** Строка ДЗ в группе: сколько сдали из скольких, сколько ждёт проверки. */
    public record HomeworkSummary(long id, String title, String issuedAt, String dueAt, boolean overdue, int maxScore,
                                  int assigned, int handedIn, int waiting) {
    }

    /** Ученик в карточке ДЗ. status — TODO / SUBMITTED / RETURNED / CHECKED; submissionId — чтобы открыть работу. */
    public record HomeworkStudent(long id, String name, String status, Integer score, Long submissionId, boolean late,
                                  int attempt) {
    }

    public record HomeworkCard(HomeworkSummary homework, TutorView.Ref group, List<StudentView.PartDto> parts,
                               List<HomeworkStudent> students) {
    }

    /**
     * Форма выдачи ДЗ. title — пусто: из первой строки текста или «ДЗ от 20.10»; assignees — null: вся группа.
     * Срок: dueDate + dueTime («2026-10-25», «20:00» в часовом поясе репетитора), или dueAt — ISO-время
     * (начало следующего занятия), или ничего — без срока.
     */
    public record IssueBody(String title, String text, List<String> files, List<Long> assignees, Integer maxScore,
                            String dueDate, String dueTime, String dueAt) {
    }

    /** notified — сколько учеников получили уведомление (отозвавшие согласие не получают). */
    public record Issued(long id, int notified) {
    }

    private final HomeworkService homework;
    private final SubmissionService submissions;
    private final HomeworkNotifier homeworkNotifier;
    private final MaterialService materials;
    private final MaterialsNotifier materialsNotifier;
    private final FileService files;
    private final StudentView studentView;
    private final ScheduleService schedule;

    public TutorContent(HomeworkService homework, SubmissionService submissions, HomeworkNotifier homeworkNotifier,
                        MaterialService materials, MaterialsNotifier materialsNotifier, FileService files,
                        StudentView studentView, ScheduleService schedule) {
        this.schedule = schedule;
        this.homework = homework;
        this.submissions = submissions;
        this.homeworkNotifier = homeworkNotifier;
        this.materials = materials;
        this.materialsNotifier = materialsNotifier;
        this.files = files;
        this.studentView = studentView;
    }

    // ---------- ДЗ ----------

    @Transactional
    public Issued issue(long groupId, long tutorId, IssueBody body) {
        List<Part> parts = new ContentBody(body.text(), body.files()).parts(files, tutorId);
        Instant due = body.dueDate() != null && !body.dueDate().isBlank()
                ? schedule.toInstant(ScheduleInput.date(body.dueDate()), ScheduleInput.time(body.dueTime()))
                : parseDue(body.dueAt());
        long id = homework.createDraft(groupId).getId();
        for (Part p : parts) {
            homework.addPart(id, p);
        }
        if (body.assignees() != null) {
            homework.setAssignees(id, body.assignees());
        }
        if (body.maxScore() != null) {
            homework.setMaxScore(id, body.maxScore());
        }
        homework.setDue(id, due);
        homework.setTitle(id, body.title());
        Homework issued = homework.issue(id);
        return new Issued(id, homeworkNotifier.issued(issued));
    }

    /** Последние выданные ДЗ группы — для карточки группы. */
    @Transactional(readOnly = true)
    public List<HomeworkSummary> forGroup(long groupId) {
        return homework.issuedForGroup(groupId).stream()
                .map(h -> summary(h, submissions.forHomework(h.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public HomeworkCard card(long homeworkId) {
        Homework hw = homework.find(homeworkId).filter(h -> h.getStatus() == HomeworkStatus.ISSUED).orElseThrow();
        List<Submission> subs = submissions.forHomework(homeworkId);
        Map<Long, Submission> byStudent = subs.stream()
                .collect(Collectors.toMap(s -> s.getStudent().getId(), Function.identity()));
        List<HomeworkStudent> students = hw.getAssignees().stream()
                .sorted(Comparator.comparing(User::displayName))
                .map(u -> student(u, byStudent.get(u.getId())))
                .toList();
        List<StudentView.PartDto> parts = hw.items().stream().map(i -> studentView.part(i.toPart(), i.getId())).toList();
        return new HomeworkCard(summary(hw, subs), new TutorView.Ref(hw.getGroup().getId(), hw.getGroup().getName()),
                parts, students);
    }

    /** Удаление выданного ДЗ вместе со сданными работами и оценками. Возвращает id группы. */
    public long delete(long homeworkId) {
        return homework.delete(homeworkId).orElseThrow().getGroup().getId();
    }

    // ---------- материалы занятия ----------

    /**
     * Материалы одной формой: добавить и сразу отправить группе (в боте это были «части» и кнопка «Готово»).
     */
    @Transactional
    public MaterialsNotifier.Sent addMaterials(long lessonId, long tutorId, ContentBody body) {
        for (Part p : body.parts(files, tutorId)) {
            materials.addPart(lessonId, p);
        }
        return materialsNotifier.published(materials.publish(lessonId));
    }

    public void deleteMaterial(long materialId) {
        materials.delete(materialId).orElseThrow();
    }

    // ---------- вспомогательное ----------

    private static Instant parseDue(String dueAt) {
        if (dueAt == null || dueAt.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(dueAt);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Не понял срок сдачи");
        }
    }

    private static HomeworkSummary summary(Homework h, List<Submission> subs) {
        int handedIn = (int) subs.stream().filter(s -> s.getStatus() != SubmissionStatus.DRAFT).count();
        int waiting = (int) subs.stream().filter(s -> s.getStatus() == SubmissionStatus.SUBMITTED).count();
        return new HomeworkSummary(h.getId(), h.getTitle(), h.getIssuedAt() == null ? null : h.getIssuedAt().toString(),
                h.getDueAt() == null ? null : h.getDueAt().toString(),
                h.getDueAt() != null && Instant.now().isAfter(h.getDueAt()), h.getMaxScore(), h.getAssignees().size(),
                handedIn, waiting);
    }

    private static HomeworkStudent student(User u, Submission s) {
        if (s == null || s.getStatus() == SubmissionStatus.DRAFT) {
            return new HomeworkStudent(u.getId(), u.displayName(), "TODO", null, null, false, 0);
        }
        return new HomeworkStudent(u.getId(), u.displayName(), s.getStatus().name(),
                s.getStatus() == SubmissionStatus.CHECKED ? s.getScore() : null, s.getId(), s.isLate(), s.getAttempt());
    }
}
