package ru.repitbot.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLogin(String login);

    boolean existsByLogin(String login);

    Optional<User> findFirstByRole(Role role);

    boolean existsByRole(Role role);

    List<User> findByRoleOrderByIdAsc(Role role);

    List<User> findByDeleteRequestedAtBeforeOrderByDeleteRequestedAtAsc(Instant before);
}
