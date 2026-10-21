package ru.repitbot.domain.billing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentInputTest {

    private static final PaymentInput.Terms EIGHT_FOR_12K = new PaymentInput.Terms(8, 12000);

    @Test
    void acceptsCommonForms() {
        assertEquals(EIGHT_FOR_12K, PaymentInput.parse("8 12000"));
        assertEquals(EIGHT_FOR_12K, PaymentInput.parse("8 12 000"));
        assertEquals(EIGHT_FOR_12K, PaymentInput.parse("8 12000 ₽"));
        assertEquals(EIGHT_FOR_12K, PaymentInput.parse("8 занятий 12000 руб"));
        assertEquals(new PaymentInput.Terms(4, 0), PaymentInput.parse("4 0"));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> PaymentInput.parse("0 100"));
        assertThrows(IllegalArgumentException.class, () -> PaymentInput.parse("101 100"));
        assertThrows(IllegalArgumentException.class, () -> PaymentInput.parse("8"));
        assertThrows(IllegalArgumentException.class, () -> PaymentInput.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> PaymentInput.parse("8 12000,50"));
        assertThrows(IllegalArgumentException.class, () -> PaymentInput.parse("8 99999999999"));
    }

    @Test
    void formatsMoneyWithSpaces() {
        assertEquals("12 000 ₽", PaymentInput.money(12000));
        assertEquals("500 ₽", PaymentInput.money(500));
    }
}
