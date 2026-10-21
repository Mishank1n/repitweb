package ru.repitbot.domain.invite;

import org.junit.jupiter.api.Test;
import ru.repitbot.domain.group.StudyGroup;
import ru.repitbot.domain.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InviteTest {

    @Test
    void codeIsUrlSafe() {
        // Код идёт в адрес /join/CODE: только A-Z a-z 0-9 _ -
        String code = InviteService.generateCode();
        assertEquals(24, code.length());
        assertTrue(code.matches("[A-Za-z0-9_-]+"), code);
    }

    @Test
    void codesAreUnique() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            codes.add(InviteService.generateCode());
        }
        assertEquals(10_000, codes.size());
    }

    @Test
    void inviteIsUsableOnlyOnceAndBeforeExpiry() {
        Instant now = Instant.now();
        Invite invite = Invite.forStudent("code", new StudyGroup("Группа"), now.plus(Duration.ofDays(7)));

        assertTrue(invite.isUsable(now));
        assertFalse(invite.isUsable(now.plus(Duration.ofDays(8))), "просроченное приглашение");

        invite.markUsed(null);
        assertFalse(invite.isUsable(now), "использованное приглашение");
    }

    @Test
    void revokedInviteIsNotUsable() {
        Instant now = Instant.now();
        Invite invite = Invite.forStudent("code", new StudyGroup("Группа"), now.plus(Duration.ofDays(7)));

        assertTrue(invite.revoke());
        assertFalse(invite.isUsable(Instant.now()));
    }

    @Test
    void usedInviteCannotBeRevoked() {
        Invite invite = Invite.forStudent("code", new StudyGroup("Группа"), Instant.now().plus(Duration.ofDays(7)));
        invite.markUsed(null);

        assertFalse(invite.revoke());
    }

    @Test
    void parentInviteHasParentRole() {
        Invite invite = Invite.forParent("code", new User("pupil", "hash", ru.repitbot.domain.user.Role.STUDENT, "Europe/Moscow"),
                Instant.now().plusSeconds(60));
        assertEquals(ru.repitbot.domain.user.Role.PARENT, invite.getType().role());
    }
}
