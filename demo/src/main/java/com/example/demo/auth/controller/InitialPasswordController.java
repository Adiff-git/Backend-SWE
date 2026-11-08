package com.example.demo.auth.controller;

import com.example.demo.auth.dto.request.InitialPasswordRequest;
import com.example.demo.auth.service.InitialPasswordService;
import com.example.demo.common.identity.CurrentUserIdProvider;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequiredArgsConstructor
@ConditionalOnProperty(name="security.verification.enabled",havingValue="true")
public class InitialPasswordController {
    private final InitialPasswordService passwords;
    private final CurrentUserIdProvider identity;
    @PostMapping(value="/api/me/password",consumes="application/json")
    public ResponseEntity<Void> set(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody InitialPasswordRequest body){
        passwords.set(identity.getCurrentUserId(),UUID.fromString(jwt.getClaimAsString("sid")),body.getNewPassword(),body.getRecentAuthProof());return ResponseEntity.noContent().header("Cache-Control","no-store").build();
    }
}
