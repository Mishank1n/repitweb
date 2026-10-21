package ru.repitbot.domain.material;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.repitbot.domain.schedule.Lesson;

import java.util.Collection;
import java.util.List;

public interface LessonMaterialRepository extends JpaRepository<LessonMaterial, Long> {

    List<LessonMaterial> findByLesson_IdOrderByPositionAsc(Long lessonId);

    List<LessonMaterial> findByLesson_IdAndPublishedOrderByPositionAsc(Long lessonId, boolean published);

    long countByLesson_IdAndPublished(Long lessonId, boolean published);

    long countByLesson_Id(Long lessonId);

    @Query("select coalesce(max(m.position), -1) from LessonMaterial m where m.lesson.id = :lessonId")
    int maxPosition(@Param("lessonId") Long lessonId);

    /** Занятия групп, у которых есть опубликованные материалы, сначала последние. */
    @Query("select l from Lesson l where l.group.id in :groupIds and exists "
            + "(select 1 from LessonMaterial m where m.lesson = l and m.published = true) order by l.startsAt desc")
    List<Lesson> lessonsWithPublished(@Param("groupIds") Collection<Long> groupIds, Pageable page);
}
