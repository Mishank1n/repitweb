package ru.repitbot.domain.billing;

/** О чём сообщить после списания. Каждое событие у абонемента бывает не больше одного раза. */
public enum BillingEvent {
    /** Осталось одно занятие. */
    LOW,
    /** Абонемент закончился. */
    CLOSED
}
