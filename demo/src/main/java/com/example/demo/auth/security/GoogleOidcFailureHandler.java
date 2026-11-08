package com.example.demo.auth.security;

import com.example.demo.auth.config.IdentityProperties;
import com.example.demo.auth.service.GoogleAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

@RequiredArgsConstructor
public class GoogleOidcFailureHandler implements AuthenticationFailureHandler {
    private final GoogleAttemptService attempts;
    private final IdentityProperties properties;
    public void onAuthenticationFailure(HttpServletRequest request,HttpServletResponse response,AuthenticationException failure) throws IOException {
        Object value=request.getAttribute(GoogleCookieSupport.ATTEMPT_ATTRIBUTE);
        if(value instanceof UUID id){try{attempts.fail(id);}catch(RuntimeException unavailable){ /* Expiry still rejects state; never expose persistence failures in the redirect. */ }}
        String code=failure instanceof OAuth2AuthenticationException oauth?oauth.getError().getErrorCode():"identity_invalid";
        String category="access_denied".equals(code)?"cancelled":("invalid_token_response".equals(code)?"provider_unavailable":"identity_invalid");
        response.sendRedirect(properties.getFrontendFailureUri()+"?error="+category);
    }
}
