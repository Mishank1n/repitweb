package ru.repitbot.domain.homework;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.domain.group.GroupService;
import ru.repitbot.domain.group.StudyGroup;
import ru.repitbot.domain.schedule.Lesson;
import ru.repitbot.domain.schedule.ScheduleService;
import ru.repitbot.domain.user.User;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/** ДЗ глазами репетитора: черновик → выдача → удаление. Все изменения черновика только до выдачи. */
@Service
public class HomeworkService {

    /** Насколько далеко искать следующее занятие для срока «до следующего занятия». */
    private static final int NEXT_LESSON_DAYS = 60;

    private final HomeworkRepository homework;
    private final GroupService groups;
    private final ScheduleService schedule;

    public HomeworkService(HomeworkRepository homework, GroupService groups, ScheduleService schedule) {
        this.homework = homework;
        this.groups = groups;
        this.schedule = schedule;
    }

    /** Новый черновик: по умолчанию выдаётся всей группе. */
    @Transactional
    public Homework createDraft(long groupId) {
        StudyGroup group = groups.find(groupId).orElseThrow();
        return homework.save(new Homework(group, groups.students(groupId)));
    }

    @Transactional
    public int addPart(long homeworkId, Part part) {
        return draft(homeworkId).addPart(part);
    }

    /** Включить или исключить ученика. Только участники группы этого ДЗ. */
    @Transactional
    public Homework toggleAssignee(long homeworkId, long studentId) {
        Homework hw = draft(homeworkId);
        User student = groups.students(hw.getGroup().getId()).stream()
                .filter(u -> u.getId() == studentId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ученик не состоит в группе"));
        hw.toggleAssignee(student);
        return hw;
    }

    @Transactional
    public Homework assignAll(long homeworkId) {
        Homework hw = draft(homeworkId);
        hw.setAssignees(groups.students(hw.getGroup().getId()));
        return hw;
    }

    /** Получатели списком (форма сайта): только ученики группы этого ДЗ, хотя бы один. */
    @Transactional
    public Homework setAssignees(long homeworkId, Collection<Long> studentIds) {
        Homework hw = draft(homeworkId);
        List<User> members = groups.students(hw.getGroup().getId());
        Set<Long> ids = new HashSet<>(studentIds);
        List<User> chosen = members.stream().filter(u -> ids.contains(u.getId())).toList();
        if (chosen.size() != ids.size()) {
            throw new IllegalArgumentException("Ученик не состоит в группе");
        }
        if (chosen.isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы одного ученика");
        }
        hw.setAssignees(chosen);
        return hw;
    }

    @Transactional
    public Homework setTitle(long homeworkId, String title) {
        Homework hw = draft(homeworkId);
        hw.setTitle(title);
        return hw;
    }

    @Transactional
    public Homework setMaxScore(long homeworkId, int maxScore) {
        Homework hw = draft(homeworkId);
        hw.setMaxScore(maxScore);
        return hw;
    }

    @Transactional
    public Homework setDue(long homeworkId, Instant dueAt) {
        if (dueAt != null && !dueAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Это время уже прошло");
        }
        Homework hw = draft(homeworkId);
        hw.setDueAt(dueAt);
        return hw;
    }

    @Transactional
    public Homework issue(long homeworkId) {
        Homework hw = draft(homeworkId);
        hw.issue(HomeworkTitles.byDate(schedule.today()));
        return hw;
    }

    /** Отмена черновика. Выданное ДЗ так не удалить — для этого {@link #delete}. */
    @Transactional
    public void discardDraft(long homeworkId) {
        homework.findById(homeworkId)
                .filter(h -> h.getStatus() == HomeworkStatus.DRAFT)
                .ifPresent(homework::delete);
    }

    @Transactional
    public Optional<Homework> delete(long homeworkId) {
        Optional<Homework> hw = homework.findById(homeworkId);
        hw.ifPresent(homework::delete);
        return hw;
    }

    @Transactional(readOnly = true)
    public Optional<Homework> find(long homeworkId) {
        return homework.findById(homeworkId);
    }

    /** Выданное ДЗ, если ученик в числе получателей; иначе пусто (в т.ч. при подделанной кнопке). */
    @Transactional(readOnly = true)
    public Optional<Homework> findForStudent(long homeworkId, long studentId) {
        return homework.findById(homeworkId)
                .filter(h -> h.getStatus() == HomeworkStatus.ISSUED && h.isAssigned(studentId));
    }

    @Transactional(readOnly = true)
    public List<Homework> issuedForGroup(long groupId) {
        return homework.findTop15ByGroup_IdAndStatusOrderByIssuedAtDesc(groupId, HomeworkStatus.ISSUED);
    }

    @Transactional(readOnly = true)
    public List<Homework> issuedForStudent(long studentId) {
        return homework.findAssigned(studentId, HomeworkStatus.ISSUED).stream().limit(15).toList();
    }

    /** Выданные ДЗ со сроком в интервале — для напоминаний. */
    @Transactional(readOnly = true)
    public List<Homework> dueBetween(Instant from, Instant to) {
        return homework.findByStatusAndDueAtBetween(HomeworkStatus.ISSUED, from, to);
    }

    /** Начало следующего занятия группы — срок по умолчанию. */
    @Transactional(readOnly = true)
    public Optional<Instant> nextLessonStart(long groupId) {
        return schedule.upcomingForGroups(List.of(groupId), NEXT_LESSON_DAYS).stream()
                .findFirst()
                .map(Lesson::getStartsAt);
    }

    private Homework draft(long homeworkId) {
        Homework hw = homework.findById(homeworkId)
                .orElseThrow(() -> new NoSuchElementException("ДЗ не найдено"));
        if (hw.getStatus() != HomeworkStatus.DRAFT) {
            throw new IllegalStateException("ДЗ уже выдано");
        }
        return hw;
    }
}
