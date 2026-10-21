package ru.repitbot.domain.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LessonSeriesRepository extends JpaRepository<LessonSeries, Long> {

    List<LessonSeries> findByGroup_IdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(Long groupId);

    List<LessonSeries> findByActiveTrue();
}
