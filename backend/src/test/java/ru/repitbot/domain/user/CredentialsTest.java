package ru.repitbot.domain.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialsTest {

    private final Credentials credentials = new Credentials();

    @Test
    void loginIsNormalized() {
        assertEquals("anna.k", Credentials.normalizeLogin("  Anna.K "));
        assertEquals("", Credentials.normalizeLogin(null));
    }

    @Test
    void loginRules() {
        assertDoesNotThrow(() -> Credentials.validLogin("anna_2010"));
        assertThrows(IllegalArgumentException.class, () -> Credentials.validLogin("an"), "короткий");
        assertThrows(IllegalArgumentException.class, () -> Credentials.validLogin("анна"), "кириллица");
        assertThrows(IllegalArgumentException.class, () -> Credentials.validLogin(".anna"), "с точки");
        assertThrows(IllegalArgumentException.class, () -> Credentials.validLogin("a".repeat(33)), "длинный");
    }

    @Test
    void passwordRules() {
        assertDoesNotThrow(() -> Credentials.validatePassword("Совсем-не-простой-7", "anna"));
        assertThrows(IllegalArgumentException.class, () -> Credentials.validatePassword("short1", "anna"));
        assertThrows(IllegalArgumentException.class, () -> Credentials.validatePassword("anna.k12", "Anna.K12"), "как логин");
        assertThrows(IllegalArgumentException.class, () -> Credentials.validatePassword("12345678", "anna"), "частый");
        // 40 кириллических букв = 80 байт: BCrypt молча обрезал бы хвост.
        assertThrows(IllegalArgumentException.class, () -> Credentials.validatePassword("ж".repeat(40), "anna"));
    }

    @Test
    void hashIsSaltedAndVerifies() {
        String a = credentials.hash("Пароль-для-теста");
        String b = credentials.hash("Пароль-для-теста");
        assertNotEquals(a, b, "у каждого хэша своя соль");
        assertTrue(credentials.matches("Пароль-для-теста", a));
        assertFalse(credentials.matches("пароль-для-теста", a));
    }

    @Test
    void unknownUserNeverMatches() {
        assertFalse(credentials.matches("dummy-password-for-timing", null));
        assertFalse(credentials.matches(null, credentials.hash("x-y-z-1234")));
    }
}
