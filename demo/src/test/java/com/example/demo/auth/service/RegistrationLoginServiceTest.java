package com.example.demo.auth.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class RegistrationLoginServiceTest extends AuthCommandTestSupport {
    @Test void execute_shouldRejectInvalidPasswordBeforeCreatingAccount() {
        assertThatThrownBy(()->commands.register("Alice","short")).isInstanceOf(com.example.demo.user.exception.BusinessValidationException.class);
        verifyNoInteractions(users,sessions,manager);
    }
    @Test void execute_shouldRejectOversizedLoginBeforeBcryptCanTruncate() {
        assertThatThrownBy(()->commands.login("Alice","a".repeat(73))).isInstanceOf(com.example.demo.auth.exception.AuthenticationFailedException.class);
        verifyNoInteractions(manager);
    }
}
