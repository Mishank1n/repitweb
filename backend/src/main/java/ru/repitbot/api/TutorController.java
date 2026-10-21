package ru.repitbot.api;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.repitbot.api.auth.SessionAuth;
import ru.repitbot.domain.file.FileService;

import java.util.List;
import java.util.Map;

/** Экраны и действия репетитора: /api/tutor/** (не-репетитору {@link SessionAuth} отвечает 403). */
@RestController
@RequestMapping("/api/tutor")
public class TutorController {

    public record MarksBody(Map<Long, String> marks) {
    }

    public record PaymentBody(int lessons, int amount, boolean closeCurrent) {
    }

    public record GradeBody(int score, String comment) {
    }

    public record CommentBody(String comment) {
    }

    private final TutorView view;
    private final FileService files;

    public TutorController(TutorView view, FileService files) {
        this.view = view;
        this.files = files;
    }

    @GetMapping("/today")
    public TutorView.Today today() {
        return view.today();
    }

    @GetMapping("/week")
    public List<TutorView.TutorLesson> week() {
        return view.week();
    }

    @GetMapping("/lessons/{lessonId}")
    public TutorView.LessonDetail lesson(@PathVariable long lessonId) {
        return view.lessonDetail(lessonId);
    }

    @PostMapping("/lessons/{lessonId}/attendance")
    public TutorView.LessonDetail mark(@PathVariable long lessonId, @RequestBody MarksBody body) {
        if (body.marks() == null || body.marks().isEmpty()) {
            throw new IllegalArgumentException("Нет отметок");
        }
        view.mark(lessonId, body.marks());
        return view.lessonDetail(lessonId);
    }

    @GetMapping("/groups")
    public List<TutorView.GroupDto> groups() {
        return view.groups();
    }

    @GetMapping("/students/{studentId}")
    public TutorView.StudentCard student(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @PathVariable long studentId) {
        return view.student(me, studentId);
    }

    @PostMapping("/students/{studentId}/payment")
    public TutorView.StudentCard payment(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @PathVariable long studentId,
                                         @RequestBody PaymentBody body) {
        view.payment(studentId, body.lessons(), body.amount(), body.closeCurrent());
        return view.student(me, studentId);
    }

    @GetMapping("/queue")
    public List<TutorView.QueueRow> queue() {
        return view.queue();
    }

    @GetMapping("/submissions/{submissionId}")
    public TutorView.SubmissionDetail submission(@PathVariable long submissionId) {
        return view.submission(submissionId);
    }

    @PostMapping("/submissions/{submissionId}/grade")
    public TutorView.SubmissionDetail grade(@PathVariable long submissionId, @RequestBody GradeBody body) {
        view.grade(submissionId, body.score(), body.comment());
        return view.submission(submissionId);
    }

    @PostMapping("/submissions/{submissionId}/return")
    public TutorView.SubmissionDetail returnForRework(@PathVariable long submissionId, @RequestBody CommentBody body) {
        view.returnForRework(submissionId, body.comment());
        return view.submission(submissionId);
    }

    /** Файл из ответа ученика — только репетитору (путь под /api/tutor). */
    @GetMapping("/media/sub/{itemId}")
    public ResponseEntity<Resource> submissionFile(@PathVariable long itemId) {
        return MediaController.serve(files, view.submissionFile(itemId));
    }
}
