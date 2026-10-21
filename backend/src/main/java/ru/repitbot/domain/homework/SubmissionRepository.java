package ru.repitbot.domain.homework;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByHomework_IdAndStudent_Id(Long homeworkId, Long studentId);

    List<Submission> findByHomework_Id(Long homeworkId);

    List<Submission> findByStatusOrderBySubmittedAtAsc(SubmissionStatus status);

    long countByStatus(SubmissionStatus status);
}
