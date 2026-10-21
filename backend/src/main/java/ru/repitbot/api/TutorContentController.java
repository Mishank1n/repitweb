package ru.repitbot.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.repitbot.api.auth.SessionAuth;
import ru.repitbot.notify.MaterialsNotifier;

import java.util.Map;

/** ДЗ и материалы занятий: /api/tutor/** (не-репетитору {@link SessionAuth} отвечает 403). */
@RestController
@RequestMapping("/api/tutor")
public class TutorContentController {

    private final TutorContent content;

    public TutorContentController(TutorContent content) {
        this.content = content;
    }

    @PostMapping("/groups/{groupId}/homework")
    public TutorContent.Issued issue(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @PathVariable long groupId,
                                     @RequestBody TutorContent.IssueBody body) {
        return content.issue(groupId, me.userId(), body);
    }

    @GetMapping("/homework/{homeworkId}")
    public TutorContent.HomeworkCard homework(@PathVariable long homeworkId) {
        return content.card(homeworkId);
    }

    @PostMapping("/homework/{homeworkId}/delete")
    public Map<String, Long> deleteHomework(@PathVariable long homeworkId) {
        return Map.of("groupId", content.delete(homeworkId));
    }

    @PostMapping("/lessons/{lessonId}/materials")
    public MaterialsNotifier.Sent addMaterials(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @PathVariable long lessonId,
                                               @RequestBody ContentBody body) {
        return content.addMaterials(lessonId, me.userId(), body);
    }

    @PostMapping("/materials/{materialId}/delete")
    public Map<String, Boolean> deleteMaterial(@PathVariable long materialId) {
        content.deleteMaterial(materialId);
        return Map.of("ok", true);
    }
}
