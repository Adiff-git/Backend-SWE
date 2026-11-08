package com.example.demo.auth.controller;

import com.example.demo.auth.converter.GoogleAttemptConverter;
import com.example.demo.auth.dto.request.GoogleConnectionAttemptRequest;
import com.example.demo.auth.dto.request.GoogleReauthenticationAttemptRequest;
import com.example.demo.auth.dto.response.GoogleAttemptResponse;
import com.example.demo.auth.dto.response.GoogleConnectionResponse;
import com.example.demo.auth.entity.OAuthLoginAttempt;
import com.example.demo.auth.security.GoogleCookieSupport;
import com.example.demo.auth.service.GoogleAttemptService;
import com.example.demo.auth.service.GoogleAttemptStart;
import com.example.demo.auth.service.GoogleConnectionService;
import com.example.demo.common.identity.CurrentUserIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequiredArgsConstructor
@ConditionalOnProperty(name="security.identity.enabled",havingValue="true")
public class GoogleConnectionController {
    private final GoogleConnectionService connections;
    private final GoogleAttemptService attempts;
    private final GoogleAttemptConverter converter;
    private final CurrentUserIdProvider identity;
    @GetMapping("/api/me/google-connection")
    public ResponseEntity<GoogleConnectionResponse> get(){return ResponseEntity.ok().header("Cache-Control","no-store").body(connections.getConnection(identity.getCurrentUserId()));}
    @PostMapping(value="/api/me/google-connection-attempts",consumes="application/json")
    public ResponseEntity<GoogleAttemptResponse> link(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody GoogleConnectionAttemptRequest body,HttpServletRequest request){
        return response(attempts.start(body.getCodeChallenge(),body.getCodeChallengeMethod(),GoogleCookieSupport.read(request),OAuthLoginAttempt.Purpose.LINK,OAuthLoginAttempt.Operation.CONNECT_GOOGLE,
            identity.getCurrentUserId(),UUID.fromString(jwt.getClaimAsString("sid")),body.getRecentAuthProof()));
    }
    @PostMapping(value="/api/me/google-reauthentication-attempts",consumes="application/json")
    public ResponseEntity<GoogleAttemptResponse> reauthenticate(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody GoogleReauthenticationAttemptRequest body,HttpServletRequest request){
        return response(attempts.start(body.getCodeChallenge(),body.getCodeChallengeMethod(),GoogleCookieSupport.read(request),OAuthLoginAttempt.Purpose.REAUTH,OAuthLoginAttempt.Operation.valueOf(body.getOperation()),
            identity.getCurrentUserId(),UUID.fromString(jwt.getClaimAsString("sid")),null));
    }
    private ResponseEntity<GoogleAttemptResponse> response(GoogleAttemptStart start){
        return ResponseEntity.status(201).header("Cache-Control","no-store").header("Set-Cookie",ResponseCookie.from(GoogleCookieSupport.NAME,start.getCookie()).path("/").secure(true).httpOnly(true).sameSite("Lax").maxAge(300).build().toString()).body(converter.toResponse(start));
    }
}
