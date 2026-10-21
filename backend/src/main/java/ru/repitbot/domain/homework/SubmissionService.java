package ru.repitbot.domain.homework;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Сдача и проверка ДЗ. Права проверяются здесь, а не только в кнопках:
 * ученик работает только со своими сдачами по выданным ему ДЗ.
 */
@Service
public class SubmissionService {

    private final SubmissionRepository submissions;
    private final HomeworkService homework;
    private final UserRepository users;

    public SubmissionService(SubmissionRepository submissions, HomeworkService homework, UserRepository users) {
        this.submissions = submissions;
        this.homework = homework;
        this.users = users;
    }

    /** Начать сдачу или продолжить черновик; после доработки — новая попытка. */
    @Transactional
    public Submission startOrResume(long homeworkId, long studentId) {
        Homework hw = homework.findForStudent(homeworkId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Это задание вам не выдано"));
        Optional<Submission> existing = submissions.findByHomework_IdAndStudent_Id(homeworkId, studentId);
        if (existing.isEmpty()) {
            User student = users.findById(studentId).orElseThrow();
            return submissions.save(new Submission(hw, student));
        }
        Submission sub = existing.get();
        if (sub.getStatus() == SubmissionStatus.RETURNED) {
            sub.reopen();
        } else if (sub.getStatus() != SubmissionStatus.DRAFT) {
            throw new IllegalStateException(sub.getStatus() == SubmissionStatus.SUBMITTED
                    ? "Работа уже отправлена и ждёт проверки"
                    : "Работа уже проверена");
        }
        return sub;
    }

    @Transactional
    public int addPart(long submissionId, long studentId, Part part) {
        return own(submissionId, studentId).addPart(part);
    }

    @Transactional
    public Submission submit(long submissionId, long studentId) {
        Submission sub = own(submissionId, studentId);
        sub.submit(Instant.now());
        return sub;
    }

    /** Отмена черновика ответа. */
    @Transactional
    public void discardDraft(long submissionId, long studentId) {
        Submission sub = own(submissionId, studentId);
        if (sub.getStatus() == SubmissionStatus.DRAFT && sub.discardDraft()) {
            submissions.delete(sub);
        }
    }

    /** Только для репетитора: доступ закрыт на уровне маршрутизации кнопок. */
    @Transactional
    public Submission grade(long submissionId, int score, String comment) {
        Submission sub = submissions.findById(submissionId).orElseThrow();
        sub.grade(score, comment);
        return sub;
    }

    @Transactional
    public Submission returnForRework(long submissionId, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Напишите, что нужно доработать");
        }
        if (comment.length() > HomeworkTitles.MAX_COMMENT) {
            throw new IllegalArgumentException("Комментарий длиннее " + HomeworkTitles.MAX_COMMENT + " символов");
        }
        Submission sub = submissions.findById(submissionId).orElseThrow();
        sub.returnForRework(comment.strip());
        return sub;
    }

    @Transactional(readOnly = true)
    public Optional<Submission> find(long submissionId) {
        return submissions.findById(submissionId);
    }

    @Transactional(readOnly = true)
    public Optional<Submission> forStudent(long homeworkId, long studentId) {
        return submissions.findByHomework_IdAndStudent_Id(homeworkId, studentId);
    }

    @Transactional(readOnly = true)
    public List<Submission> forHomework(long homeworkId) {
        return submissions.findByHomework_Id(homeworkId);
    }

    /** Очередь на проверку: старые сверху. */
    @Transactional(readOnly = true)
    public List<Submission> queue() {
        return submissions.findByStatusOrderBySubmittedAtAsc(SubmissionStatus.SUBMITTED);
    }

    @Transactional(readOnly = true)
    public long queueSize() {
        return submissions.countByStatus(SubmissionStatus.SUBMITTED);
    }

    /** Сдачи, которые считаются «сданными» для напоминаний о сроке: отправлено, проверено или на доработке. */
    public static boolean handedIn(Optional<Submission> sub) {
        return sub.map(s -> s.getStatus() != SubmissionStatus.DRAFT).orElse(false);
    }

    private Submission own(long submissionId, long studentId) {
        return submissions.findById(submissionId)
                .filter(s -> s.getStudent().getId() == studentId)
                .orElseThrow(() -> new IllegalArgumentException("Это не ваша работа"));
    }
}
