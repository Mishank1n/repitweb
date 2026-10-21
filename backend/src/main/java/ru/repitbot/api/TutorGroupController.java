package ru.repitbot.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Группы, приглашения, ученики и расписание репетитора: /api/tutor/** (не-репетитору SessionAuth отвечает 403).
 * Ошибки ввода — 400 с текстом для человека (ApiErrors), чужой или удалённый id — 404.
 */
@RestController
@RequestMapping("/api/tutor")
public class TutorGroupController {

    public record NameBody(String name) {
    }

    public record PlaceBody(String type, String value) {
    }

    public record SeriesBody(Integer day, String time, Integer duration) {
    }

    public record LessonBody(String date, String time, Integer duration) {
    }

    private final TutorGroups groups;

    public TutorGroupController(TutorGroups groups) {
        this.groups = groups;
    }

    // ---------- группы ----------

    @GetMapping("/groups/{groupId}")
    public TutorGroups.GroupDetail group(@PathVariable long groupId) {
        return groups.group(groupId);
    }

    @PostMapping("/groups")
    public TutorGroups.GroupDetail create(@RequestBody NameBody body) {
        return groups.group(groups.create(body.name()));
    }

    @PostMapping("/groups/{groupId}/rename")
    public TutorGroups.GroupDetail rename(@PathVariable long groupId, @RequestBody NameBody body) {
        groups.rename(groupId, body.name());
        return groups.group(groupId);
    }

    @PostMapping("/groups/{groupId}/delete")
    public Map<String, Boolean> delete(@PathVariable long groupId) {
        groups.delete(groupId);
        return Map.of("ok", true);
    }

    @PostMapping("/groups/{groupId}/place")
    public TutorGroups.GroupDetail place(@PathVariable long groupId, @RequestBody PlaceBody body) {
        groups.place(groupId, body.type(), body.value());
        return groups.group(groupId);
    }

    // ---------- приглашения ----------

    @PostMapping("/groups/{groupId}/invite")
    public TutorGroups.InviteLink invite(@PathVariable long groupId) {
        return groups.inviteStudent(groupId);
    }

    @GetMapping("/students/{studentId}/parent-invites")
    public List<TutorGroups.InviteLink> parentInvites(@PathVariable long studentId) {
        return groups.parentInvites(studentId);
    }

    @PostMapping("/students/{studentId}/parent-invite")
    public TutorGroups.InviteLink parentInvite(@PathVariable long studentId) {
        return groups.inviteParent(studentId);
    }

    @PostMapping("/users/{userId}/reset-link")
    public TutorGroups.InviteLink resetLink(@PathVariable long userId) {
        return groups.resetLink(userId);
    }

    @PostMapping("/invites/{inviteId}/revoke")
    public Map<String, String> revoke(@PathVariable long inviteId) {
        return Map.of("result", groups.revoke(inviteId).name());
    }

    // ---------- ученики и родители ----------

    @PostMapping("/groups/{groupId}/students/{studentId}/remove")
    public TutorGroups.GroupDetail removeStudent(@PathVariable long groupId, @PathVariable long studentId) {
        groups.removeStudent(groupId, studentId);
        return groups.group(groupId);
    }

    @PostMapping("/students/{studentId}/parents/{parentId}/unlink")
    public Map<String, Boolean> unlinkParent(@PathVariable long studentId, @PathVariable long parentId) {
        groups.unlinkParent(studentId, parentId);
        return Map.of("ok", true);
    }

    @PostMapping("/users/{userId}/delete")
    public Map<String, Boolean> deleteUser(@PathVariable long userId) {
        groups.deleteUser(userId);
        return Map.of("ok", true);
    }

    @GetMapping("/deletion-requests")
    public List<TutorGroups.DeletionRequest> deletionRequests() {
        return groups.deletionRequests();
    }

    // ---------- расписание ----------

    @PostMapping("/groups/{groupId}/series")
    public TutorGroups.GroupDetail addSeries(@PathVariable long groupId, @RequestBody SeriesBody body) {
        groups.addSeries(groupId, body.day(), body.time(), body.duration());
        return groups.group(groupId);
    }

    @PostMapping("/series/{seriesId}/delete")
    public Map<String, Boolean> removeSeries(@PathVariable long seriesId) {
        groups.removeSeries(seriesId);
        return Map.of("ok", true);
    }

    @PostMapping("/groups/{groupId}/lessons")
    public Map<String, Long> addLesson(@PathVariable long groupId, @RequestBody LessonBody body) {
        return Map.of("id", groups.addLesson(groupId, body.date(), body.time(), body.duration()));
    }

    @PostMapping("/lessons/{lessonId}/move")
    public Map<String, Boolean> move(@PathVariable long lessonId, @RequestBody LessonBody body) {
        groups.moveLesson(lessonId, body.date(), body.time());
        return Map.of("ok", true);
    }

    @PostMapping("/lessons/{lessonId}/cancel")
    public Map<String, Boolean> cancel(@PathVariable long lessonId) {
        groups.cancelLesson(lessonId);
        return Map.of("ok", true);
    }
}
