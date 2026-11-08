package com.example.demo.auth.security;

import com.example.demo.auth.service.GoogleAttemptService;
import com.example.demo.auth.service.GoogleAuthorizationState;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

@RequiredArgsConstructor
public class PostgresAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private final GoogleAttemptService attempts;
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        GoogleAuthorizationState state=attempts.loadAuthorization(request.getParameter("state"),GoogleCookieSupport.read(request),false);
        return state==null?null:state.getRequest();
    }
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorization,HttpServletRequest request,HttpServletResponse response) {
        if(authorization!=null)attempts.saveAuthorization(request.getParameter("attempt"),GoogleCookieSupport.read(request),authorization);
    }
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,HttpServletResponse response) {
        GoogleAuthorizationState state=attempts.loadAuthorization(request.getParameter("state"),GoogleCookieSupport.read(request),true);
        if(state!=null)request.setAttribute(GoogleCookieSupport.ATTEMPT_ATTRIBUTE,state.getAttemptId());
        return state==null?null:state.getRequest();
    }
}
