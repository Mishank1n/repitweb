package ru.repitbot.domain.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Attendance.Key> {

    List<Attendance> findByLesson_IdOrderByStudent_IdAsc(Long lessonId);

    List<Attendance> findByStudent_IdAndLesson_StartsAtGreaterThanEqualAndLesson_StartsAtLessThanOrderByLesson_StartsAtAsc(
            Long studentId, Instant from, Instant to);

    /** Самое раннее занятие, списанное из абонемента: его переносим при исправлении отметки. */
    Optional<Attendance> findTopBySubscription_IdOrderByLesson_StartsAtAsc(Long subscriptionId);

    /** Списываемые занятия без абонемента, отмеченные после момента since. */
    @Query("select count(a) from Attendance a where a.student.id = :studentId and a.subscription is null "
            + "and a.status <> ru.repitbot.domain.billing.AttendanceStatus.ABSENT_FREE and a.markedAt > :since")
    long countOutside(@Param("studentId") Long studentId, @Param("since") Instant since);
}
