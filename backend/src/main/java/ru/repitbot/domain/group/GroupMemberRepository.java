package ru.repitbot.domain.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroup_IdOrderByJoinedAtAsc(Long groupId);

    List<GroupMember> findByStudent_IdOrderByJoinedAtAsc(Long studentId);

    boolean existsByGroup_IdAndStudent_Id(Long groupId, Long studentId);

    long countByGroup_Id(Long groupId);

    long deleteByGroup_IdAndStudent_Id(Long groupId, Long studentId);
}
