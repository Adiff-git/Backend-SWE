package com.example.demo.auth.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class LogoutServiceTest extends AuthCommandTestSupport {
    @Test void execute_shouldRecognizeConsumedCredentialButAbsoluteExpiryWins() {
        credential.setConsumedAt(now.minusSeconds(1));
        assertThat(commands.logout("secret").isAccepted()).isTrue();
        verify(sessions).revoke(session,now,"LOGOUT");
        reset(sessions);
        when(sessions.findCredentialEntity(new byte[32])).thenReturn(java.util.Optional.of(credential));
        when(sessions.findSessionEntity(session.getId())).thenReturn(java.util.Optional.of(session));
        when(sessions.getSessionEntityForUpdate(session.getId())).thenReturn(session);
        when(sessions.getCredentialEntityForUpdate(2L)).thenReturn(credential);
        session.setExpiresAt(now);
        assertThat(commands.logout("secret").isAccepted()).isFalse();
        verify(sessions,never()).revoke(any(),any(),anyString());
    }
}
