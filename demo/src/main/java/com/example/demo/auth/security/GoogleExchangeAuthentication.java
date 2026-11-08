package com.example.demo.auth.security;

import com.example.demo.auth.exception.IdentityException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="security.identity.enabled",havingValue="true")
public class GoogleExchangeAuthentication {
    private final JwtDecoder decoder;
    private final CurrentStateJwtConverter converter;
    public Jwt require(HttpServletRequest request) {
        try {
            String value=new DefaultBearerTokenResolver().resolve(request);
            if(value==null)throw invalid();
            Jwt jwt=decoder.decode(value);org.springframework.security.authentication.AbstractAuthenticationToken current=converter.convert(jwt);
            if(current==null)throw invalid();
            if(current.getAuthorities().stream().noneMatch(authority->"MANAGE_USER_PROFILE".equals(authority.getAuthority())))throw new IdentityException(403,"ACCOUNT_OPERATION_FORBIDDEN","Profile permission is required");
            return jwt;
        }catch(org.springframework.security.core.AuthenticationException | org.springframework.security.oauth2.jwt.JwtException | IllegalArgumentException failure){throw invalid();}
    }
    private IdentityException invalid(){return new IdentityException(401,"HANDOFF_INVALID","Authentication failed");}
}
