package ru.repitbot.domain.homework;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface HomeworkRepository extends JpaRepository<Homework, Long> {

    List<Homework> findTop15ByGroup_IdAndStatusOrderByIssuedAtDesc(Long groupId, HomeworkStatus status);

    @Query("select h from Homework h join h.assignees a where a.id = :studentId and h.status = :status order by h.issuedAt desc")
    List<Homework> findAssigned(@Param("studentId") Long studentId, @Param("status") HomeworkStatus status);

    List<Homework> findByStatusAndDueAtBetween(HomeworkStatus status, Instant from, Instant to);

    /** ДЗ ученика, у которых в периоде выдача, срок или проверка его работы — для недельного отчёта. */
    @Query("select h from Homework h join h.assignees a where a.id = :studentId and h.status = :status and ("
            + "h.issuedAt >= :from and h.issuedAt < :to or h.dueAt >= :from and h.dueAt < :to or exists ("
            + "select 1 from Submission s where s.homework = h and s.student.id = :studentId "
            + "and s.checkedAt >= :from and s.checkedAt < :to)) order by h.issuedAt")
    List<Homework> findActiveForStudent(@Param("studentId") Long studentId, @Param("status") HomeworkStatus status,
                                        @Param("from") Instant from, @Param("to") Instant to);
}
