package com.example.demo.auth.controller;

import com.example.demo.auth.config.IdentityProperties;
import com.example.demo.auth.converter.GoogleAttemptConverter;
import com.example.demo.auth.dto.request.GoogleAttemptRequest;
import com.example.demo.auth.dto.request.GoogleCodeExchangeRequest;
import com.example.demo.auth.dto.response.GoogleAttemptResponse;
import com.example.demo.auth.entity.OAuthHandoff;
import com.example.demo.auth.entity.OAuthLoginAttempt;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.security.GoogleCookieSupport;
import com.example.demo.auth.security.GoogleExchangeAuthentication;
import com.example.demo.auth.service.GoogleAttemptService;
import com.example.demo.auth.service.GoogleAttemptStart;
import com.example.demo.auth.service.GoogleExchangeOutcome;
import com.example.demo.auth.service.GoogleExchangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
@ConditionalOnProperty(name="security.identity.enabled",havingValue="true")
public class GoogleLoginController {
    private final GoogleAttemptService attempts;
    private final GoogleExchangeService exchanges;
    private final GoogleAttemptConverter converter;
    private final GoogleExchangeAuthentication authentication;
    private final IdentityProperties properties;
    @PostMapping(value="/google-login-attempts",consumes="application/json")
    public ResponseEntity<GoogleAttemptResponse> start(@Valid @RequestBody GoogleAttemptRequest body,HttpServletRequest request) {
        requireOrigin(request);
        GoogleAttemptStart start=attempts.start(body.getCodeChallenge(),body.getCodeChallengeMethod(),GoogleCookieSupport.read(request),
            OAuthLoginAttempt.Purpose.LOGIN,null,null,null,null);
        ResponseCookie cookie=ResponseCookie.from(GoogleCookieSupport.NAME,start.getCookie()).path("/").secure(true).httpOnly(true).sameSite("Lax").maxAge(300).build();
        return ResponseEntity.status(201).header("Cache-Control","no-store").header("Set-Cookie",cookie.toString()).body(converter.toResponse(start));
    }
    @PostMapping(value="/google-code-exchanges",consumes="application/json")
    public ResponseEntity<Object> exchange(@Valid @RequestBody GoogleCodeExchangeRequest body,HttpServletRequest request) {
        requireOrigin(request);
        Jwt bearer=exchanges.purpose(body.getCode())==OAuthHandoff.Purpose.LOGIN?null:authentication.require(request);
        GoogleExchangeOutcome result=exchanges.exchange(body.getCode(),body.getCodeVerifier(),bearer);
        ResponseEntity.BodyBuilder response=ResponseEntity.ok().header("Cache-Control","no-store");
        if(result.getTokens()!=null)response.header("Authorization","Bearer "+result.getTokens().getAccessToken());
        return response.body(result.getBody());
    }
    private void requireOrigin(HttpServletRequest request) {
        String origin=request.getHeader("Origin");
        if(origin!=null && !properties.getAllowedOrigins().contains(origin))throw new IdentityException(403,"ORIGIN_NOT_ALLOWED","Origin is not allowed");
    }
}
