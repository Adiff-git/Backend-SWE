package com.example.demo.auth.service;
import com.example.demo.auth.service.impl.AuthTransactionServiceImpl;
import com.example.demo.auth.validator.PasswordPolicyValidator;
import com.example.demo.user.service.UserService;
import com.example.demo.user.dto.response.UserSecuritySnapshot;
import com.example.demo.user.enums.AccountStatus;
import com.example.demo.auth.entity.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import static org.mockito.Mockito.*;
abstract class AuthCommandTestSupport {
    UserService users;
    AuthSessionService sessions;
    TokenService tokens;
    PasswordEncoder encoder;
    AuthenticationManager manager;
    AuthTransactionServiceImpl commands;
    Instant now=Instant.parse("2026-10-26T00:00:00Z");
    AuthenticationSession session;
    RefreshCredential credential;
    @BeforeEach void setup() {
        users=mock(UserService.class);
        sessions=mock(AuthSessionService.class);
        tokens=mock(TokenService.class);
        encoder=mock(PasswordEncoder.class);
        manager=mock(AuthenticationManager.class);
        commands=new AuthTransactionServiceImpl(users,sessions,tokens,new PasswordPolicyValidator(),encoder,manager,Clock.fixed(now,ZoneOffset.UTC));
        session=new AuthenticationSession();
        session.setId(UUID.randomUUID());
        session.setUserId(1L);
        session.setExpiresAt(now.plusSeconds(604800));
        credential=new RefreshCredential();
        credential.setId(2L);
        credential.setSessionId(session.getId());
        when(tokens.hash("secret")).thenReturn(new byte[32]);
        when(sessions.findCredentialEntity(new byte[32])).thenReturn(Optional.of(credential));
        when(sessions.findSessionEntity(session.getId())).thenReturn(Optional.of(session));
        when(sessions.getSessionEntityForUpdate(session.getId())).thenReturn(session);
        when(sessions.getCredentialEntityForUpdate(2L)).thenReturn(credential);
        when(users.getUserSecuritySnapshot(1L)).thenReturn(new UserSecuritySnapshot(1L,"Alice","encoded",AccountStatus.ACTIVE,Set.of("ROLE_USER")));
    }
}
