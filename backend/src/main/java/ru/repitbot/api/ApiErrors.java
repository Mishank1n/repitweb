package ru.repitbot.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import ru.repitbot.domain.invite.InviteException;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Ошибки действий и форм (неверные баллы, логин занят, ссылка устарела…) — на сайте понятным текстом.
 */
@RestControllerAdvice(basePackages = "ru.repitbot.api")
public class ApiErrors {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, InviteException.class})
    public ResponseEntity<Map<String, String>> invalid(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid", "message", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge() {
        return ResponseEntity.badRequest().body(Map.of("error", "too_large", "message", "Файл больше 20 МБ"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
    }
}
