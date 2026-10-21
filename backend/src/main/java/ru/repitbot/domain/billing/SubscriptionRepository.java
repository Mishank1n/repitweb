package ru.repitbot.domain.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByStudent_IdAndClosedAtIsNull(Long studentId);

    List<Subscription> findTop5ByStudent_IdOrderByPaidAtDesc(Long studentId);

    Optional<Subscription> findTopByStudent_IdOrderByPaidAtDesc(Long studentId);

    /** Открытые абонементы, где осталось не больше одного занятия. */
    @Query("select s from Subscription s where s.closedAt is null and s.lessonsUsed >= s.lessonsTotal - 1 order by s.student.firstName")
    List<Subscription> findEndingSoon();
}
