package com.example.demo.auth.security;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.auth.entity.AuthenticationSession;
import com.example.demo.user.service.UserService;
import com.example.demo.user.dto.response.UserSecuritySnapshot;
import com.example.demo.user.enums.AccountStatus;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class CurrentStateJwtConverterTest {
    @Test void execute_shouldUseCurrentAuthoritiesAndRejectsForeignRevokedExpiredOrDisabledState() {
        var users=mock(UserService.class);
        var sessions=mock(AuthSessionService.class);
        Instant now=Instant.now();
        UUID sid=UUID.randomUUID();
        var jwt=Jwt.withTokenValue("test").header("alg","HS256").subject("1").claim("sid",sid.toString()).claim("scope","ADMIN").issuedAt(now).expiresAt(now.plusSeconds(100)).build();
        var session=new AuthenticationSession();
        session.setId(sid);
        session.setUserId(1L);
        session.setExpiresAt(now.plusSeconds(200));
        when(sessions.findSessionEntity(sid)).thenReturn(Optional.of(session));
        when(users.getUserSecuritySnapshot(1L)).thenReturn(new UserSecuritySnapshot(1L,"Alice","encoded",AccountStatus.ACTIVE,Set.of("MANAGE_ADDRESS")));
        var converter=new CurrentStateJwtConverter(users,sessions,Clock.fixed(now,ZoneOffset.UTC));
        assertThat(converter.convert(jwt).getAuthorities()).extracting("authority").containsExactly("MANAGE_ADDRESS");
        session.setUserId(2L);
        assertInvalid(converter,jwt);
        session.setUserId(1L);
        session.setRevokedAt(now);
        assertInvalid(converter,jwt);
        session.setRevokedAt(null);
        session.setExpiresAt(now);
        assertInvalid(converter,jwt);
        session.setExpiresAt(now.plusSeconds(200));
        when(users.getUserSecuritySnapshot(1L)).thenReturn(new UserSecuritySnapshot(1L,"Alice","encoded",AccountStatus.SUSPENDED,Set.of()));
        assertInvalid(converter,jwt);
        when(sessions.findSessionEntity(sid)).thenReturn(Optional.empty());
        assertInvalid(converter,jwt);
    }
    void assertInvalid(CurrentStateJwtConverter converter,Jwt jwt) {
        assertThatThrownBy(()->converter.convert(jwt)).isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
    }
}
