package com.example.demo.auth.integration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.demo.auth.service.*;
import com.example.demo.auth.entity.AuthenticationSession;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class RegistrationLoginServiceIT extends AuthPostgresTestSupport {
    @Autowired AuthService auth;
    @MockitoSpyBean TokenService tokens;
    @MockitoSpyBean AuthSessionService sessionService;
    @Test void execute_shouldVerifySigningFailureRollsBackAccountRoleSessionAndCredential() {
        doThrow(new IllegalStateException("Controlled signing failure")).when(tokens).issue(any(),anyString());
        assertThatThrownBy(()->auth.register("Alice","Password 123")).isInstanceOf(IllegalStateException.class);
        assertThat(users.count()).isZero();
        assertThat(sessions.count()).isZero();
        assertThat(credentials.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_roles",Long.class)).isZero();
    }
    @Test void execute_shouldVerifyRefreshSigningFailureRestoresConsumedCredential() throws Exception {
        Pair p=register("Alice");
        doThrow(new IllegalStateException("Controlled signing failure")).when(tokens).issue(any(),anyString());
        assertThatThrownBy(()->auth.refresh(p.refresh)).isInstanceOf(IllegalStateException.class);
        assertThat(credentials.count()).isEqualTo(1);
        assertThat(credentials.findAll().getFirst().getConsumedAt()).isNull();
        assertThat(sessions.findAll().getFirst().getRevokedAt()).isNull();
        assertThat(access(p)).isEqualTo(200);
    }
    @Test void execute_shouldVerifyRevocationFailureRollsBackPasswordAndAllSessionChanges() throws Exception {
        Pair p=register("Alice");
        AuthenticationSession session=sessions.findAll().getFirst();
        String before=users.findAll().getFirst().getPassword();
        doAnswer(invocation-> {
            invocation.callRealMethod();throw new IllegalStateException("Controlled revocation failure");
        }
        ).when(sessionService).revokeAll(anyLong(),any());
        assertThatThrownBy(()->auth.changePassword(session.getUserId(),session.getId(),"Password 123","Replacement 123")).isInstanceOf(IllegalStateException.class);
        assertThat(users.findAll().getFirst().getPassword()).isEqualTo(before);
        assertThat(sessions.findAll().getFirst().getRevokedAt()).isNull();
        assertThat(access(p)).isEqualTo(200);
    }
    @Test void execute_shouldVerifyCleanupFailureRestoresExpiredFamily() throws Exception {
        register("Expired");
        var owner=users.findAll().getFirst().getId();
        jdbc.update("UPDATE authentication_sessions SET created_at=created_at-INTERVAL '8 days',expires_at=expires_at-INTERVAL '8 days'");
        doAnswer(invocation-> {
            invocation.callRealMethod();throw new IllegalStateException("Controlled cleanup failure");
        }
        ).when(sessionService).purgeExpired(anyLong(),any());
        assertThatThrownBy(()->commands.cleanupOwner(owner,java.time.Instant.now())).isInstanceOf(IllegalStateException.class);
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(credentials.count()).isEqualTo(1);
    }
    @Autowired AuthTransactionService commands;
    @Test void register_shouldRollbackAccountWhenDefaultRoleIsUnavailable() {
        jdbc.update("UPDATE roles SET name='UNAVAILABLE' WHERE name='USER'");
        try {
            assertThatThrownBy(()->auth.register("Alice","Password 123")).isInstanceOf(IllegalStateException.class);
            assertThat(users.count()).isZero();
            assertThat(sessions.count()).isZero();
            assertThat(credentials.count()).isZero();
        }
        finally {
            jdbc.update("UPDATE roles SET name='USER' WHERE name='UNAVAILABLE'");
        }
    }
}
