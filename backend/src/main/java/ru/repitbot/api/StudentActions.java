package ru.repitbot.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.domain.file.FileService;
import ru.repitbot.domain.group.GroupService;
import ru.repitbot.domain.homework.Part;
import ru.repitbot.domain.homework.Submission;
import ru.repitbot.domain.homework.SubmissionService;
import ru.repitbot.domain.schedule.Lesson;
import ru.repitbot.domain.schedule.ScheduleService;
import ru.repitbot.domain.user.Role;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;
import ru.repitbot.notify.HomeworkNotifier;
import ru.repitbot.notify.LessonNotifier;
import ru.repitbot.notify.SendLog;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Действия ученика и родителя на сайте: сдать работу, предупредить «не смогу прийти».
 * Кто вообще может открыть данные ученика, уже проверил {@link StudentView#student}; здесь — кто может действовать.
 */
@Service
public class StudentActions {

    private final SubmissionService submissions;
    private final HomeworkNotifier homeworkNotifier;
    private final ScheduleService schedule;
    private final GroupService groups;
    private final UserService users;
    private final SendLog sendLog;
    private final LessonNotifier lessonNotifier;
    private final FileService files;

    public StudentActions(SubmissionService submissions, HomeworkNotifier homeworkNotifier, ScheduleService schedule,
                          GroupService groups, UserService users, SendLog sendLog, LessonNotifier lessonNotifier,
                          FileService files) {
        this.submissions = submissions;
        this.homeworkNotifier = homeworkNotifier;
        this.schedule = schedule;
        this.groups = groups;
        this.users = users;
        this.sendLog = sendLog;
        this.lessonNotifier = lessonNotifier;
        this.files = files;
    }

    /**
     * Ответ одной формой: текст и файлы сразу уходят на проверку, репетитор получает уведомление.
     * Сдаёт только сам ученик; после «на доработку» — следующая попытка, прошлая сохраняется.
     */
    @Transactional
    public void submit(ApiUser me, User student, long homeworkId, ContentBody body) {
        if (me.role() != Role.STUDENT || me.userId() != student.getId()) {
            throw new NoSuchElementException();
        }
        var parts = body.parts(files, student.getId());
        Submission sub = submissions.startOrResume(homeworkId, student.getId());
        for (Part p : parts) {
            submissions.addPart(sub.getId(), student.getId(), p);
        }
        homeworkNotifier.submitted(submissions.submit(sub.getId(), student.getId()));
    }

    /**
     * «Не смогу прийти»: ученик о себе или родитель о ребёнке. Отметка одна на ученика и занятие
     * (её же учитывает недельный отчёт); после переноса занятия можно предупредить снова.
     */
    @Transactional
    public void skip(ApiUser me, User student, long lessonId) {
        if (me.role() == Role.TUTOR) {
            throw new NoSuchElementException();
        }
        Lesson lesson = schedule.find(lessonId)
                .filter(l -> groups.isMember(l.getGroup().getId(), student.getId()))
                .orElseThrow();
        if (!lesson.isUpcoming(Instant.now())) {
            throw new IllegalStateException("Занятие уже началось или отменено");
        }
        if (!sendLog.claim(lessonId, student.getId(), SendLog.SKIP)) {
            throw new IllegalStateException("Репетитор уже предупреждён");
        }
        User reporter = users.findById(me.userId()).orElseThrow();
        lessonNotifier.skipped(lesson, student, reporter);
    }
}
