package ru.repitbot.domain.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParentLinkRepository extends JpaRepository<ParentLink, Long> {

    List<ParentLink> findByParent_Id(Long parentId);

    List<ParentLink> findByStudent_Id(Long studentId);

    boolean existsByParent_IdAndStudent_Id(Long parentId, Long studentId);

    long deleteByParent_IdAndStudent_Id(Long parentId, Long studentId);
}
