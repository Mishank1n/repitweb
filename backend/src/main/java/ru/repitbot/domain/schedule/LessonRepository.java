package ru.repitbot.domain.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByStatusAndStartsAtBetweenOrderByStartsAtAsc(LessonStatus status, Instant from, Instant to);

    List<Lesson> findByGroup_IdInAndStatusAndStartsAtBetweenOrderByStartsAtAsc(
            Collection<Long> groupIds, LessonStatus status, Instant from, Instant to);

    List<Lesson> findTop15ByGroup_IdAndStatusInAndStartsAtBeforeOrderByStartsAtDesc(
            Long groupId, Collection<LessonStatus> statuses, Instant before);

    boolean existsBySeries_IdAndSeriesDate(Long seriesId, LocalDate seriesDate);

    List<Lesson> findBySeries_IdAndStatusAndStartsAtAfter(Long seriesId, LessonStatus status, Instant after);
}
