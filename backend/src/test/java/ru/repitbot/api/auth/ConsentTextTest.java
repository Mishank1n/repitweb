package ru.repitbot.api.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsentTextTest {

    @Test
    void studentConsentNamesTutorAndUnder14Rule() {
        String text = ConsentText.render("Анна Иванова", null);
        assertTrue(text.contains("вы даёте Анна Иванова"));
        assertTrue(text.contains("меньше 14 лет"));
        assertTrue(text.contains("152-ФЗ"));
        assertTrue(text.contains("в России"));
        assertFalse(text.contains("Telegram"));
    }

    @Test
    void parentConsentMentionsChild() {
        String text = ConsentText.render(null, "Петя");
        assertTrue(text.contains("вы даёте репетитору"));
        assertTrue(text.contains("(Петя)"));
        assertTrue(text.contains("данных вашего ребёнка"));
        assertFalse(text.contains("меньше 14 лет"));
    }
}
