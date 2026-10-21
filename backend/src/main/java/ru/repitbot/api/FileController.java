package ru.repitbot.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.repitbot.api.auth.SessionAuth;
import ru.repitbot.domain.file.FileService;
import ru.repitbot.domain.user.Role;

/**
 * Загрузка файла для задания, ответа или материала. Сам по себе файл ни к чему не привязан:
 * его id отправляется вместе с формой, а непривязанные файлы через сутки удаляет уборка.
 */
@RestController
public class FileController {

    private final FileService files;

    public FileController(FileService files) {
        this.files = files;
    }

    @PostMapping("/api/files")
    public FileService.Uploaded upload(@RequestAttribute(SessionAuth.ATTR) ApiUser me, @RequestParam("file") MultipartFile file) {
        return files.store(file, me.userId(), me.role() != Role.TUTOR);
    }
}
