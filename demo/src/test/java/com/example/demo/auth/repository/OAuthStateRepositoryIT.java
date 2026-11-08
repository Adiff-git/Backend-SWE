package com.example.demo.auth.repository;

import com.example.demo.auth.entity.OAuthBrowserSlot;
import com.example.demo.auth.entity.OAuthLoginAttempt;
import com.example.demo.user.integration.PostgresTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OAuthStateRepositoryIT extends PostgresTestSupport {
    @Autowired private OAuthBrowserSlotRepository slots;
    @Autowired private OAuthLoginAttemptRepository attempts;
    @Autowired private jakarta.persistence.EntityManager entities;

    @Test void findForUpdate_shouldRoundTripBrowserGenerationAndFixedDeadline() {
        OAuthLoginAttempt attempt = attempt();
        attempts.saveAndFlush(attempt);
        entities.clear();
        OAuthLoginAttempt loaded = attempts.findForUpdate(attempt.getId()).orElseThrow();
        assertThat(loaded.getBrowserGeneration()).isEqualTo(1);
        assertThat(loaded.getPkceChallenge()).isEqualTo("a".repeat(43));
        assertThat(loaded.getExpiresAt()).isEqualTo(loaded.getCreatedAt().plusSeconds(300));
        assertThat(attempts.findByStartTicketHash(new byte[32])).isPresent();
    }
    @Test void save_shouldRejectInvalidLifetimeAndAnonymousLinking() {
        OAuthLoginAttempt attempt = attempt();
        attempt.setExpiresAt(attempt.getExpiresAt().plusSeconds(1));
        assertThatThrownBy(() -> attempts.saveAndFlush(attempt)).isInstanceOf(DataIntegrityViolationException.class);
    }
    private OAuthLoginAttempt attempt() {
        Instant now = Instant.parse("2026-11-05T00:00:00Z");
        OAuthBrowserSlot slot = new OAuthBrowserSlot();
        slot.setId(UUID.randomUUID()); slot.setSlotSecretHash(new byte[32]);
        slot.setGeneration(1); slot.setExpiresAt(now.plusSeconds(300));
        slots.saveAndFlush(slot);
        OAuthLoginAttempt attempt = new OAuthLoginAttempt();
        attempt.setId(UUID.randomUUID()); attempt.setStartTicketHash(new byte[32]);
        attempt.setPurpose(OAuthLoginAttempt.Purpose.LOGIN);
        attempt.setDestinationKey("default"); attempt.setPkceChallenge("a".repeat(43));
        attempt.setBrowserSlotId(slot.getId()); attempt.setBrowserGeneration(1);
        attempt.setBrowserBindingHash(new byte[32]); attempt.setStatus(OAuthLoginAttempt.Status.CREATED);
        attempt.setCreatedAt(now); attempt.setExpiresAt(now.plusSeconds(300));
        return attempt;
    }
}
