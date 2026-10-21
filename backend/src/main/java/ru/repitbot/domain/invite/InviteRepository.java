package ru.repitbot.domain.invite;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InviteRepository extends JpaRepository<Invite, Long> {

    Optional<Invite> findByCode(String code);

    /** Действующие приглашения в группу (ещё не использованы и не истекли), новые первыми. */
    List<Invite> findByGroup_IdAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(Long groupId, Instant now);

    /** Действующие приглашения родителя к ученику. */
    List<Invite> findByStudent_IdAndTypeAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(Long studentId, InviteType type,
                                                                                     Instant now);

    /** Блокировка строки, чтобы одну ссылку не приняли дважды одновременно. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invite i where i.code = :code")
    Optional<Invite> findByCodeForUpdate(@Param("code") String code);
}
