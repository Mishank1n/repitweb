package ru.repitbot.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.repitbot.api.auth.SessionAuth;
import ru.repitbot.domain.user.User;

import java.util.List;

/**
 * Экраны ученика: /api/students/{id}/… Открыть может сам ученик, его родитель и репетитор
 * (проверяет {@link StudentView#student}); чужой id — 404, как будто его нет.
 */
@RestController
@RequestMapping("/api/students/{studentId}")
public class StudentController {

    private final StudentView view;
    private final StudentActions actions;

    public StudentController(StudentView view, StudentActions actions) {
        this.view = view;
        this.actions = actions;
    }

    @GetMapping("/home")
    public StudentView.Home home(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @PathVariable long studentId) {
        return view.home(me, student(me, studentId));
    }

    @GetMapping("/schedule")
    public List<StudentView.LessonDto> schedule(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @PathVariable long studentId) {
        return view.schedule(student(me, studentId));
    }

    @GetMapping("/homework")
    public List<StudentView.HomeworkRow> homework(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @PathVariable long studentId) {
        return view.homeworkRows(student(me, studentId));
    }

    @GetMapping("/homework/{homeworkId}")
    public StudentView.HomeworkDetail homeworkDetail(@RequestAttribute(SessionAuth.ATTR) ApiUser me,
                                                     @PathVariable long studentId, @PathVariable long homeworkId) {
        return view.homeworkDetail(me, student(me, studentId), homeworkId).orElseThrow(StudentController::notFound);
    }

    /** Сдать работу (или исправленную после доработки): текст и id загруженных файлов. */
    @PostMapping("/homework/{homeworkId}/submit")
    public StudentView.HomeworkDetail submit(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @PathVariable long studentId,
                                             @PathVariable long homeworkId, @RequestBody ContentBody body) {
        User student = student(me, studentId);
        actions.submit(me, student, homeworkId, body);
        return view.homeworkDetail(me, student, homeworkId).orElseThrow(StudentController::notFound);
    }

    /** «Не смогу прийти» — репетитор получает уведомление. */
    @PostMapping("/lessons/{lessonId}/skip")
    public List<StudentView.LessonDto> skip(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @PathVariable long studentId,
                                            @PathVariable long lessonId) {
        User student = student(me, studentId);
        actions.skip(me, student, lessonId);
        return view.schedule(student);
    }

    @GetMapping("/materials")
    public List<StudentView.MaterialLesson> materials(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @PathVariable long studentId) {
        return view.materials(student(me, studentId));
    }

    @GetMapping("/materials/{lessonId}")
    public StudentView.MaterialDetail material(@RequestAttribute(SessionAuth.ATTR) ApiUser me,
                                              @PathVariable long studentId, @PathVariable long lessonId) {
        return view.materialDetail(me, student(me, studentId), lessonId).orElseThrow(StudentController::notFound);
    }

    private User student(ApiUser me, long studentId) {
        return view.student(me, studentId).orElseThrow(StudentController::notFound);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
