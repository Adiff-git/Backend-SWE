package com.example.demo.auth.security;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.auth.entity.AuthenticationSession;
import com.example.demo.user.service.UserService;
import com.example.demo.user.dto.response.UserSecuritySnapshot;
import com.example.demo.user.enums.AccountStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public class CurrentStateJwtConverter implements Converter<Jwt,AbstractAuthenticationToken> {
    private final UserService users;
    private final AuthSessionService sessions;
    private final Clock clock;
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            Long owner=Long.valueOf(jwt.getSubject());
            AuthenticationSession session=sessions.findSessionEntity(UUID.fromString(jwt.getClaimAsString("sid"))).orElseThrow(this::invalid);
            UserSecuritySnapshot user=users.getUserSecuritySnapshot(owner);
            if(!owner.equals(session.getUserId()) || !session.isActiveAt(clock.instant()) || jwt.getExpiresAt().isAfter(session.getExpiresAt()) || user.getAccountStatus()!=AccountStatus.ACTIVE)throw invalid();
            return new JwtAuthenticationToken(jwt,user.getAuthorities().stream().map(SimpleGrantedAuthority::new).toList(),owner.toString());
        }
        catch(com.example.demo.common.exception.ApplicationException | IllegalArgumentException failure) {
            throw invalid();
        }
    }
    private OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"),"Authentication failed");
    }
}
