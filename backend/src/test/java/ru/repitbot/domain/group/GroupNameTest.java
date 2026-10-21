package ru.repitbot.domain.group;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroupNameTest {

    @Test
    void trimsName() {
        assertEquals("Анна ЕГЭ", GroupService.validName("  Анна ЕГЭ \n"));
        assertEquals("ЕГЭ английский", GroupService.validName("ЕГЭ  \t английский"));
    }

    @Test
    void rejectsEmptyAndTooLong() {
        assertThrows(IllegalArgumentException.class, () -> GroupService.validName("   "));
        assertThrows(IllegalArgumentException.class, () -> GroupService.validName(null));
        assertThrows(IllegalArgumentException.class, () -> GroupService.validName("x".repeat(StudyGroup.MAX_NAME_LENGTH + 1)));
    }
}
