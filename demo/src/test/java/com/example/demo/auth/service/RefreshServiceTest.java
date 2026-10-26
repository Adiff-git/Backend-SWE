package com.example.demo.auth.service;
import com.example.demo.auth.service.impl.AuthServiceImpl;
import com.example.demo.auth.exception.AuthenticationFailedException;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class RefreshServiceTest {
    @Test void execute_shouldMapRejectedOutcomeAfterCommandReturns() {
        var commands=mock(AuthTransactionService.class);
        when(commands.refresh("secret")).thenReturn(AuthOutcome.rejected());
        var facade=new AuthServiceImpl(commands);
        assertThatThrownBy(()->facade.refresh("secret")).isInstanceOf(AuthenticationFailedException.class);
        verify(commands).refresh("secret");
    }
    @Test void execute_shouldRefuseAmbientTransactionThatWouldRollbackReuseRevocation() {
        var commands=mock(AuthTransactionService.class);
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(()->new AuthServiceImpl(commands).refresh("secret")).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(commands);
        }
        finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
