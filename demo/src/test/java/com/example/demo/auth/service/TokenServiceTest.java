package com.example.demo.auth.service;
import com.example.demo.auth.config.*;
import com.example.demo.auth.entity.AuthenticationSession;
import com.example.demo.auth.service.impl.TokenServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TokenServiceTest {
    @Test void execute_shouldCapAccessAtSessionDeadlineAndGeneratesOpaqueSecrets() {
        Instant now=Instant.parse("2026-10-26T00:00:00Z");
        var props=new JwtProperties("a".repeat(32),"urn:test","");
        var config=new AuthSecurityConfiguration();
        var service=new TokenServiceImpl(config.jwtEncoder(props),props,Clock.fixed(now,ZoneOffset.UTC));
        var session=new AuthenticationSession();
        session.setId(UUID.randomUUID());
        session.setUserId(42L);
        session.setExpiresAt(now.plusSeconds(40));
        String raw=service.generateRefreshToken();
        assertThat(raw).matches("[A-Za-z0-9_-]{43}");
        assertThat(service.generateRefreshToken()).isNotEqualTo(raw);
        assertThat(service.hash(raw)).hasSize(32).containsExactly(service.hash(raw));
        var issued=service.issue(session,raw);
        var jwt=config.jwtDecoder(props,Clock.fixed(now,ZoneOffset.UTC)).decode(issued.getAccessToken());
        assertThat(jwt.getExpiresAt()).isEqualTo(session.getExpiresAt());
        assertThat(issued.getResponse().getAccessTokenExpiresIn()).isEqualTo(40);
        session.setExpiresAt(now);
        assertThatThrownBy(()->service.issue(session,raw)).isInstanceOf(com.example.demo.auth.exception.AuthenticationFailedException.class);
    }
}
