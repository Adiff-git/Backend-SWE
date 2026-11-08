package com.example.demo.auth.security;

import com.example.demo.auth.config.IdentityProperties;
import com.example.demo.auth.service.GoogleAttemptService;
import com.example.demo.auth.service.ValidatedGoogleIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@RequiredArgsConstructor
public class GoogleOidcSuccessHandler implements AuthenticationSuccessHandler {
    private final GoogleAttemptService attempts;
    private final IdentityProperties properties;
    public void onAuthenticationSuccess(HttpServletRequest request,HttpServletResponse response,Authentication authentication) throws IOException {
        Object value=request.getAttribute(GoogleCookieSupport.ATTEMPT_ATTRIBUTE);
        if(!(value instanceof UUID id) || !(authentication.getPrincipal() instanceof OidcUser user)) {
            response.sendRedirect(properties.getFrontendFailureUri()+"?error=identity_invalid");return;
        }
        try {
            String code=attempts.complete(id,new ValidatedGoogleIdentity(user.getSubject(),user.getEmail(),Boolean.TRUE.equals(user.getEmailVerified()),user.getFullName(),user.getIdToken().getClaimAsInstant("auth_time")));
            response.sendRedirect(properties.getFrontendSuccessUri()+"?code="+code);
        }catch(RuntimeException failure){
            try{attempts.fail(id);}catch(RuntimeException unavailable){ /* Fixed failure response even during a persistence outage. */ }
            response.sendRedirect(properties.getFrontendFailureUri()+"?error=identity_invalid");
        }
    }
}
