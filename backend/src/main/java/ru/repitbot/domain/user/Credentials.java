package ru.repitbot.domain.user;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Правила логина и пароля и хэширование паролей (BCrypt). В базе хранится только хэш.
 * Ошибки — IllegalArgumentException с текстом для человека: API показывает его как есть.
 */
@Component
public class Credentials {

    public static final int MIN_PASSWORD = 8;
    /** BCrypt учитывает только первые 72 байта: длиннее не принимаем, чтобы хвост пароля не терялся молча. */
    static final int MAX_PASSWORD_BYTES = 72;
    private static final Pattern LOGIN = Pattern.compile("[a-z0-9][a-z0-9._-]{2,31}");
    /** Самые частые пароли: их подбирают первыми. */
    private static final Set<String> COMMON = Set.of("12345678", "123456789", "1234567890", "password", "qwerty123",
            "qwertyui", "11111111", "00000000", "87654321", "йцукенгш", "password1", "qwerty12", "1q2w3e4r");

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(11);
    /** Хэш, с которым сравниваем пароль для несуществующего логина: ответ занимает столько же времени. */
    private final String dummyHash = encoder.encode("dummy-password-for-timing");

    /** «Anna.K » → «anna.k». */
    public static String normalizeLogin(String login) {
        return login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
    }

    /** Проверяет логин (уже приведённый) и возвращает его. */
    public static String validLogin(String login) {
        if (!LOGIN.matcher(login).matches()) {
            throw new IllegalArgumentException(
                    "Логин: от 3 до 32 символов — латинские буквы, цифры, точка, дефис или подчёркивание");
        }
        return login;
    }

    public static void validatePassword(String password, String login) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("Пароль должен быть не короче " + MIN_PASSWORD + " символов");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Пароль слишком длинный");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (lower.equals(normalizeLogin(login)) || COMMON.contains(lower)) {
            throw new IllegalArgumentException("Этот пароль слишком простой — придумайте другой");
        }
    }

    public String hash(String password) {
        return encoder.encode(password);
    }

    /** Совпадает ли пароль с хэшем; без пользователя — сравнение с фиктивным хэшем, чтобы время ответа не выдавало логин. */
    public boolean matches(String password, String hash) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            encoder.matches("x", dummyHash);
            return false;
        }
        return encoder.matches(password, hash == null ? dummyHash : hash) && hash != null;
    }
}
