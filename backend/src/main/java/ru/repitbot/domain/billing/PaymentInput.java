package ru.repitbot.domain.billing;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Разбор «8 12000» (число занятий и сумма одним сообщением) и формат суммы. */
public final class PaymentInput {

    public record Terms(int lessons, int amount) {
    }

    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");
    private static final Pattern FRACTION = Pattern.compile("\\d[.,]\\d");

    private PaymentInput() {
    }

    /** «8 12000», «8 12 000», «8 12000 ₽», «8 занятий 12000». */
    public static Terms parse(String text) {
        Matcher m = FIRST_NUMBER.matcher(text);
        if (!m.find()) {
            throw new IllegalArgumentException("Не вижу чисел. Пример: «8 12000» — 8 занятий за 12 000 ₽");
        }
        int lessons = toInt(m.group());
        String rest = text.substring(m.end());
        if (FRACTION.matcher(rest).find()) {
            throw new IllegalArgumentException("Сумму укажите целым числом рублей");
        }
        String amountDigits = rest.replaceAll("\\D", "");
        if (amountDigits.isEmpty()) {
            throw new IllegalArgumentException("Укажите и сумму. Пример: «8 12000»");
        }
        int amount = toInt(amountDigits);
        validate(lessons, amount);
        return new Terms(lessons, amount);
    }

    public static void validate(int lessons, int amount) {
        if (lessons < 1 || lessons > Subscription.MAX_LESSONS) {
            throw new IllegalArgumentException("Число занятий — от 1 до " + Subscription.MAX_LESSONS);
        }
        if (amount < 0 || amount > Subscription.MAX_AMOUNT) {
            throw new IllegalArgumentException("Сумма — от 0 до " + money(Subscription.MAX_AMOUNT));
        }
    }

    /** «12 000 ₽». */
    public static String money(int amount) {
        return NumberFormat.getIntegerInstance(Locale.of("ru")).format(amount).replace(' ', ' ') + " ₽";
    }

    private static int toInt(String digits) {
        return digits.length() > 9 ? Integer.MAX_VALUE : Integer.parseInt(digits);
    }
}
