package com.example.demo.auth.integration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.demo.auth.service.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AuthCleanupIT extends AuthPostgresTestSupport {
    @Autowired AuthTransactionService commands;
    @Autowired AuthSessionService sessionService;
    @Test void execute_shouldDeleteExpiredFamilyAndPreservesActiveConsumedHistory() throws Exception {
        Pair expired=register("Expired");
        Long owner=users.findAll().getFirst().getId();
        jdbc.update("UPDATE authentication_sessions SET created_at=created_at-INTERVAL '8 days',expires_at=expires_at-INTERVAL '8 days' WHERE user_id=?",owner);
        Pair active=register("Active");
        Pair replacement=pair(refresh(active.refresh).andExpect(status().isOk()).andReturn());
        assertThat(access(expired)).isEqualTo(401);
        refresh(expired.refresh).andExpect(status().isUnauthorized());
        assertThat(sessionService.findExpiredOwners(Instant.now(),1)).containsExactly(owner);
        commands.cleanupOwner(owner,Instant.now());
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(credentials.count()).isEqualTo(2);
        refresh(expired.refresh).andExpect(status().isUnauthorized());
        assertThat(access(replacement)).isEqualTo(200);
        refresh(active.refresh).andExpect(status().isUnauthorized());
        assertThat(access(replacement)).isEqualTo(401);
    }
    @Test void cleanup_shouldBoundEachTransactionAndDrainRemainingBatches() throws Exception {
        register("Expired");
        Long owner=users.findAll().getFirst().getId();
        jdbc.update("UPDATE authentication_sessions SET created_at=created_at-INTERVAL '8 days',expires_at=expires_at-INTERVAL '8 days'");
        jdbc.update("INSERT INTO authentication_sessions(id,user_id,created_at,expires_at) SELECT gen_random_uuid(),?,date_trunc('second',CURRENT_TIMESTAMP)-INTERVAL '8 days',date_trunc('second',CURRENT_TIMESTAMP)-INTERVAL '1 day' FROM generate_series(1,100)",owner);
        commands.cleanupOwner(owner,Instant.now());
        assertThat(sessions.count()).isEqualTo(1);
        new com.example.demo.auth.config.AuthCleanupScheduler(sessionService,commands,java.time.Clock.systemUTC()).cleanup();
        assertThat(sessions.count()).isZero();
        assertThat(credentials.count()).isZero();
    }
}
