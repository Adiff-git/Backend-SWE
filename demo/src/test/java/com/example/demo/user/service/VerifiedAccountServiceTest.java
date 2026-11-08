package com.example.demo.user.service;

import com.example.demo.user.converter.UserConverter;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.service.impl.UserServiceImpl;
import com.example.demo.user.validator.UserValidator;
import java.time.Instant;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerifiedAccountServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserServiceImpl service = new UserServiceImpl(users, new UserConverter(), new UserValidator(), mock(EntityManager.class), 5000, mock(com.example.demo.media.service.MediaService.class));
    private final Instant now = Instant.parse("2026-11-05T00:00:00Z");
    @Test void createVerifiedAccount_shouldDeriveSuffixWithoutLosingOfficialEmail() {
        when(users.lowercase("Alice@example.test")).thenReturn("alice@example.test");
        when(users.insertVerifiedAccount(eq("alice"), eq(null), eq("alice@example.test"), eq(now))).thenReturn(Optional.empty());
        when(users.insertVerifiedAccount(eq("alice_2"), eq(null), eq("alice@example.test"), eq(now))).thenReturn(Optional.of(7L));
        when(users.findAuthorities(7L)).thenReturn(java.util.List.of("ROLE_USER"));
        assertThat(service.createVerifiedAccount("Alice@example.test", null, now)).isEqualTo(7L);
        verify(users).assignDefaultRole(7L);
    }
    @Test void setInitialPassword_shouldNeverOverwriteExistingCredential() {
        User user = new User(); user.setPassword("already-set");
        when(users.findForUpdate(7L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.setInitialEncodedPassword(7L, "$2a$10$" + "a".repeat(53)))
                .isInstanceOf(com.example.demo.user.exception.IdentityConflictException.class);
        assertThat(user.getPassword()).isEqualTo("already-set");
    }
    @Test void replaceVerifiedEmail_shouldKeepUsernameAndBindEvidence() {
        User user = new User(); user.setUsername("original"); user.setEmail("old@example.test");
        when(users.findForUpdate(7L)).thenReturn(Optional.of(user));
        when(users.lowercase("New@example.test")).thenReturn("new@example.test");
        service.replaceVerifiedEmail(7L, "New@example.test", now);
        assertThat(user.getEmail()).isEqualTo("new@example.test");
        assertThat(user.getEmailVerifiedAt()).isEqualTo(now);
        assertThat(user.getUsername()).isEqualTo("original");
    }
}
