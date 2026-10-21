package ru.repitbot.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.domain.group.GroupService;
import ru.repitbot.domain.group.StudyGroup;
import ru.repitbot.domain.invite.Invite;
import ru.repitbot.domain.invite.InviteService;
import ru.repitbot.domain.schedule.LessonSeries;
import ru.repitbot.domain.schedule.ScheduleInput;
import ru.repitbot.domain.schedule.ScheduleService;
import ru.repitbot.domain.schedule.When;
import ru.repitbot.domain.user.Role;
import ru.repitbot.domain.user.User;
import ru.repitbot.domain.user.UserService;
import ru.repitbot.notify.LessonNotifier;
import ru.repitbot.notify.Recipients;

import java.time.Duration;
import java.util.List;

/**
 * Группы, приглашения, ученики и расписание глазами репетитора: данные экранов и действия.
 * Ученики и родители получают те же уведомления, что раньше присылал бот (через {@link LessonNotifier}).
 * Доступ — только репетитору: /api/tutor/** проверяет SessionAuth.
 */
@Service
public class TutorGroups {

    private static final int UPCOMING_DAYS = 14;
    /** Насколько далеко смотреть будущие занятия при удалении группы (серии создают их на 4 недели). */
    private static final int FUTURE_DAYS = 60;

    /** Ученик в карточке группы: родители с id — для отвязки. */
    public record Student(long id, String name, String login, String subscription, List<TutorView.Ref> parents,
                          boolean consent) {
    }

    /** Регулярное занятие: day 1–7, time «18:00», line «еженедельно, пн в 18:00, 60 мин». */
    public record Series(long id, int day, String time, int durationMin, String line) {
    }

    /** Ссылка-приглашение или на новый пароль. target — группа, ученик или человек, которому она. */
    public record InviteLink(long id, String type, String link, String expiresAt, String target) {
    }

    public record GroupDetail(long id, String name, String place, String meetingUrl, String address,
                              List<Student> students, List<Series> series, List<TutorView.TutorLesson> upcoming,
                              List<InviteLink> invites, long futureLessons, List<TutorContent.HomeworkSummary> homework) {
    }

    public record DeletionRequest(long id, String name, String role, String requestedAt) {
    }

    private final GroupService groups;
    private final ScheduleService schedule;
    private final InviteService invites;
    private final UserService users;
    private final LessonNotifier notifier;
    private final TutorView view;
    private final TutorContent content;

    public TutorGroups(GroupService groups, ScheduleService schedule, InviteService invites, UserService users,
                       LessonNotifier notifier, TutorView view, TutorContent content) {
        this.content = content;
        this.groups = groups;
        this.schedule = schedule;
        this.invites = invites;
        this.users = users;
        this.notifier = notifier;
        this.view = view;
    }

    // ---------- группа ----------

    @Transactional(readOnly = true)
    public GroupDetail group(long groupId) {
        StudyGroup g = groups.find(groupId).orElseThrow();
        List<Student> students = groups.students(groupId).stream()
                .map(s -> new Student(s.getId(), s.displayName(), s.getLogin(), view.counter(s),
                        groups.parentsOf(s.getId()).stream().map(p -> new TutorView.Ref(p.getId(), p.displayName())).toList(),
                        users.canMessage(s)))
                .toList();
        List<Series> series = schedule.activeSeries(groupId).stream()
                .map(s -> new Series(s.getId(), s.getDay().getValue(), When.time(s.getStartTime()), s.getDurationMin(),
                        LessonNotifier.seriesLine(s)))
                .toList();
        return new GroupDetail(g.getId(), g.getName(), TutorView.place(g), g.getMeetingUrl(), g.getAddress(), students,
                series,
                schedule.notEndedForGroups(List.of(groupId), UPCOMING_DAYS).stream().map(view::lesson).toList(),
                invites.activeForGroup(groupId).stream().map(i -> link(i, g.getName())).toList(),
                schedule.upcomingForGroups(List.of(groupId), FUTURE_DAYS).size(), content.forGroup(groupId));
    }

    public long create(String name) {
        return groups.create(name).getId();
    }

    public void rename(long groupId, String name) {
        groups.rename(groupId, name);
    }

    /** Удаление группы. Получателей уведомления собираем до удаления: после него состава группы уже нет. */
    public void delete(long groupId) {
        boolean hadFutureLessons = !schedule.upcomingForGroups(List.of(groupId), FUTURE_DAYS).isEmpty();
        List<Recipients.Recipient> recipients = hadFutureLessons ? notifier.recipients(groupId) : List.of();
        StudyGroup deleted = groups.delete(groupId).orElseThrow();
        notifier.groupClosed(deleted.getName(), recipients);
    }

    /** type: online (value — https-ссылка на созвон) или offline (value — адрес). */
    public void place(long groupId, String type, String value) {
        switch (type == null ? "" : type) {
            case "online" -> groups.setOnline(groupId, value);
            case "offline" -> groups.setOffline(groupId, value);
            default -> throw new IllegalArgumentException("Выберите: онлайн или очно");
        }
    }

    // ---------- приглашения и ссылки ----------

    public InviteLink inviteStudent(long groupId) {
        StudyGroup g = groups.find(groupId).orElseThrow();
        return link(invites.createForStudent(groupId), g.getName());
    }

    public InviteLink inviteParent(long studentId) {
        User student = student(studentId);
        return link(invites.createForParent(studentId), student.displayName());
    }

    @Transactional(readOnly = true)
    public List<InviteLink> parentInvites(long studentId) {
        User student = student(studentId);
        return invites.activeParentInvites(studentId).stream().map(i -> link(i, student.displayName())).toList();
    }

    /** Ссылка на новый пароль (сутки) ученику или родителю, который забыл пароль. */
    public InviteLink resetLink(long userId) {
        User user = users.findById(userId).filter(u -> u.getRole() != Role.TUTOR).orElseThrow();
        return link(invites.createReset(userId), user.displayName());
    }

    public InviteService.RevokeResult revoke(long inviteId) {
        return invites.revoke(inviteId);
    }

    // ---------- ученики и родители ----------

    public void removeStudent(long groupId, long studentId) {
        if (!groups.removeStudent(groupId, studentId)) {
            throw new IllegalArgumentException("Ученика уже нет в этой группе");
        }
    }

    public void unlinkParent(long studentId, long parentId) {
        if (!groups.unlinkParent(parentId, studentId)) {
            throw new IllegalArgumentException("Родитель уже отвязан");
        }
    }

    /** Полное удаление ученика или родителя со всеми данными (членство, связи, сессии — каскадом). */
    public void deleteUser(long userId) {
        users.delete(userId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<DeletionRequest> deletionRequests() {
        return users.deletionRequestsOlderThan(Duration.ZERO).stream()
                .map(u -> new DeletionRequest(u.getId(), u.displayName(), u.getRole().title(),
                        u.getDeleteRequestedAt().toString()))
                .toList();
    }

    // ---------- расписание ----------

    public void addSeries(long groupId, Integer day, String time, Integer duration) {
        LessonSeries s = schedule.createSeries(groupId, ScheduleInput.day(day), ScheduleInput.time(time),
                ScheduleInput.duration(duration));
        notifier.seriesCreated(s);
    }

    public void removeSeries(long seriesId) {
        LessonSeries s = schedule.deactivateSeries(seriesId)
                .orElseThrow(() -> new IllegalArgumentException("Это регулярное занятие уже удалено"));
        notifier.seriesRemoved(s);
    }

    public long addLesson(long groupId, String date, String time, Integer duration) {
        var lesson = schedule.createOneOff(groupId, ScheduleInput.date(date), ScheduleInput.time(time),
                ScheduleInput.duration(duration));
        notifier.lessonCreated(lesson);
        return lesson.getId();
    }

    public void moveLesson(long lessonId, String date, String time) {
        notifier.lessonMoved(schedule.reschedule(lessonId, ScheduleInput.date(date), ScheduleInput.time(time)));
    }

    public void cancelLesson(long lessonId) {
        var lesson = schedule.cancel(lessonId)
                .orElseThrow(() -> new IllegalArgumentException("Отменить можно только занятие, которое ещё не началось"));
        notifier.lessonCancelled(lesson);
    }

    private User student(long studentId) {
        return users.findById(studentId).filter(u -> u.getRole() == Role.STUDENT).orElseThrow();
    }

    private InviteLink link(Invite invite, String target) {
        return new InviteLink(invite.getId(), invite.getType().name(), invites.link(invite),
                invite.getExpiresAt().toString(), target);
    }
}
