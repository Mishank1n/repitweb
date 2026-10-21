package ru.repitbot.domain.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ru.repitbot.domain.user.User;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Абонемент ученика на N занятий. Открыт, пока не проведены все занятия.
 * Уведомления «осталось 1» и «закончился» приходят один раз: если из-за исправления отметки
 * абонемент откроется и закроется снова, повторного уведомления не будет.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    public static final int MAX_LESSONS = 100;
    public static final int MAX_AMOUNT = 10_000_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(name = "lessons_total", nullable = false)
    private int lessonsTotal;

    @Column(name = "lessons_used", nullable = false)
    private int lessonsUsed;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "low_warned", nullable = false)
    private boolean lowWarned;

    @Column(name = "close_notified", nullable = false)
    private boolean closeNotified;

    protected Subscription() {
    }

    public Subscription(User student, int lessonsTotal, int amount) {
        PaymentInput.validate(lessonsTotal, amount);
        this.student = student;
        this.lessonsTotal = lessonsTotal;
        this.amount = amount;
    }

    /** Списать одно занятие. На последнем занятии абонемент закрывается. */
    public Set<BillingEvent> charge() {
        if (!isOpen()) {
            throw new IllegalStateException("Абонемент уже закрыт");
        }
        lessonsUsed++;
        Set<BillingEvent> events = EnumSet.noneOf(BillingEvent.class);
        if (lessonsUsed == lessonsTotal) {
            closedAt = Instant.now();
            if (!closeNotified) {
                closeNotified = true;
                events.add(BillingEvent.CLOSED);
            }
        } else if (lessonsUsed == lessonsTotal - 1 && !lowWarned) {
            lowWarned = true;
            events.add(BillingEvent.LOW);
        }
        return events;
    }

    /** Вернуть занятие (исправили отметку). Закрытый абонемент снова открывается. */
    public void refund() {
        refundKeepClosed();
        closedAt = null;
    }

    /**
     * Вернуть занятие, не открывая абонемент: нужно, когда у ученика уже открыт следующий
     * (больше одного открытого быть не может).
     */
    public void refundKeepClosed() {
        if (lessonsUsed == 0) {
            throw new IllegalStateException("В абонементе нет списанных занятий");
        }
        lessonsUsed--;
    }

    /** Закрыть досрочно перед новой оплатой. Уведомление «закончился» не нужно: репетитор делает это сам. */
    public void closeEarly() {
        if (isOpen()) {
            closedAt = Instant.now();
            closeNotified = true;
        }
    }

    public boolean isOpen() {
        return closedAt == null;
    }

    /** «5/8». */
    public String counter() {
        return lessonsUsed + "/" + lessonsTotal;
    }

    public Long getId() {
        return id;
    }

    public User getStudent() {
        return student;
    }

    public int getLessonsTotal() {
        return lessonsTotal;
    }

    public int getLessonsUsed() {
        return lessonsUsed;
    }

    public int getAmount() {
        return amount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
