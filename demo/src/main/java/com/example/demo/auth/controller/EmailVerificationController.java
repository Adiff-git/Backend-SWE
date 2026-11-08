package com.example.demo.auth.controller;

import com.example.demo.auth.dto.request.EmailVerificationRequest;
import com.example.demo.auth.dto.request.ConfirmEmailVerificationRequest;
import com.example.demo.auth.dto.response.EmailVerificationChallengeResponse;
import com.example.demo.auth.dto.response.VerifiedEmailResponse;
import com.example.demo.auth.entity.EmailVerificationChallenge;
import com.example.demo.auth.service.EmailVerificationService;
import com.example.demo.common.identity.CurrentUserIdProvider;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/me/email-verification-challenges") @RequiredArgsConstructor
@ConditionalOnProperty(name="security.verification.enabled",havingValue="true")
public class EmailVerificationController {
    private final EmailVerificationService verification;
    private final CurrentUserIdProvider identity;
    @PostMapping(consumes="application/json")
    public ResponseEntity<EmailVerificationChallengeResponse> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody EmailVerificationRequest body){
        return ResponseEntity.status(201).header("Cache-Control","no-store").body(verification.create(identity.getCurrentUserId(),UUID.fromString(jwt.getClaimAsString("sid")),EmailVerificationChallenge.Purpose.valueOf(body.getPurpose()),body.getDestination()));
    }
    @GetMapping("/{id}") public ResponseEntity<EmailVerificationChallengeResponse> get(@PathVariable UUID id){
        return ResponseEntity.ok().header("Cache-Control","no-store").body(verification.get(identity.getCurrentUserId(),id));
    }
    @PostMapping(value="/{id}/confirm",consumes="application/json")
    public ResponseEntity<VerifiedEmailResponse> confirm(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody ConfirmEmailVerificationRequest body){
        return ResponseEntity.ok().header("Cache-Control","no-store").body(verification.confirm(identity.getCurrentUserId(),UUID.fromString(jwt.getClaimAsString("sid")),id,body.getCode(),body.getRecentAuthProof()));
    }
}
