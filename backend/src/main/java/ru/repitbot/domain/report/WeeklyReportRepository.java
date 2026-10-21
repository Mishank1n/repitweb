package ru.repitbot.domain.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    List<WeeklyReport> findByWeekStartOrderByIdAsc(LocalDate weekStart);

    Optional<WeeklyReport> findByStudent_IdAndWeekStart(Long studentId, LocalDate weekStart);
}
