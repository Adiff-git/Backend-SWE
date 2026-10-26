package com.example.demo.auth.security;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.common.identity.IdentityUnavailableException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
@Component
public class SpringSecurityCurrentUserIdProvider implements CurrentUserIdProvider {
    public Long getCurrentUserId() {
        org.springframework.security.core.Authentication authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt))throw new IdentityUnavailableException();
        return Long.valueOf(jwt.getSubject());
    }
}
