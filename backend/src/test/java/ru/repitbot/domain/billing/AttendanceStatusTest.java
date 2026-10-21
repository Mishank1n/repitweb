package ru.repitbot.domain.billing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.repitbot.domain.billing.AttendanceStatus.ABSENT_CHARGED;
import static ru.repitbot.domain.billing.AttendanceStatus.ABSENT_FREE;
import static ru.repitbot.domain.billing.AttendanceStatus.PRESENT;

class AttendanceStatusTest {

    @Test
    void encodeDecodeRoundTrip() {
        List<AttendanceStatus> list = List.of(PRESENT, ABSENT_CHARGED, ABSENT_FREE);
        assertEquals("PCF", AttendanceStatus.encode(list));
        assertEquals(list, AttendanceStatus.decode("PCF", 3));
    }

    @Test
    void toggleCyclesOneStudent() {
        assertEquals("CP", AttendanceStatus.toggle("PP", 0));
        assertEquals("FP", AttendanceStatus.toggle("CP", 0));
        assertEquals("PP", AttendanceStatus.toggle("FP", 0));
    }

    @Test
    void onlyFreeAbsenceIsNotCharged() {
        assertTrue(PRESENT.charged());
        assertTrue(ABSENT_CHARGED.charged());
        assertFalse(ABSENT_FREE.charged());
    }

    @Test
    void rejectsDamagedCodes() {
        assertThrows(IllegalArgumentException.class, () -> AttendanceStatus.decode("PX", 2));
        assertThrows(IllegalArgumentException.class, () -> AttendanceStatus.decode("PP", 3));
        assertThrows(IllegalArgumentException.class, () -> AttendanceStatus.toggle("PP", 2));
    }
}
