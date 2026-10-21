package ru.repitbot.api;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.repitbot.api.auth.SessionAuth;
import ru.repitbot.domain.file.FileService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Файлы из заданий и материалов. Сначала проверка, что пользователь может видеть эту часть,
 * потом файл отдаётся с диска. Картинки — для показа на странице, остальное — только скачиванием
 * (attachment + nosniff): загруженный HTML или скрипт никогда не выполнится в браузере.
 */
@RestController
public class MediaController {

    private final StudentView view;
    private final FileService files;

    public MediaController(StudentView view, FileService files) {
        this.view = view;
        this.files = files;
    }

    @GetMapping("/api/media/hw/{itemId}")
    public ResponseEntity<Resource> homework(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @PathVariable long itemId) {
        return serve(files, view.homeworkFile(me, itemId));
    }

    @GetMapping("/api/media/mat/{materialId}")
    public ResponseEntity<Resource> material(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @PathVariable long materialId) {
        return serve(files, view.materialFile(me, materialId));
    }

    /** Файл из своего отправленного ответа — только самому ученику (репетитору — /api/tutor/media/sub). */
    @GetMapping("/api/media/sub/{itemId}")
    public ResponseEntity<Resource> submission(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @PathVariable long itemId) {
        return serve(files, view.submissionFile(me, itemId));
    }

    /** Файл по id из таблицы files, если доступ уже проверен; иначе 404, как будто его нет. */
    static ResponseEntity<Resource> serve(FileService files, Optional<String> fileId) {
        FileService.Stored f = fileId.flatMap(files::find)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean image = f.isImage() && !f.mime().equals("image/svg+xml");
        ContentDisposition disposition = (image ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(f.name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(image ? MediaType.parseMediaType(f.mime()) : MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(f.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(new FileSystemResource(f.path()));
    }
}
