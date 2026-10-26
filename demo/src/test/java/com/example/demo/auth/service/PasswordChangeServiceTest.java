package com.example.demo.auth.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PasswordChangeServiceTest extends AuthCommandTestSupport {
    @Test void execute_shouldVerifyWrongCurrentAndUnchangedReplacementNeverMutatePasswordOrSessions() {
        assertThatThrownBy(()->commands.changePassword(1L,session.getId(),"wrong","Replacement 123")).isInstanceOf(com.example.demo.user.exception.BusinessValidationException.class);
        when(encoder.matches("same password","encoded")).thenReturn(true);
        assertThatThrownBy(()->commands.changePassword(1L,session.getId(),"same password","same password")).isInstanceOf(com.example.demo.user.exception.BusinessValidationException.class);
        verify(users,never()).replaceEncodedPassword(anyLong(),anyString());
        verify(sessions,never()).revokeAll(anyLong(),any());
    }
}
