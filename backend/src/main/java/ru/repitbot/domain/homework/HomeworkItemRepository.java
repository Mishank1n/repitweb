package ru.repitbot.domain.homework;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeworkItemRepository extends JpaRepository<HomeworkItem, Long> {
}
