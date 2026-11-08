package com.example.demo.auth.controller;

import com.example.demo.auth.dto.request.ReauthenticationRequest;
import com.example.demo.auth.dto.response.RecentAuthProofResponse;
import com.example.demo.auth.entity.RecentAuthProof;
import com.example.demo.auth.service.RecentAuthenticationService;
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
public class ReauthenticationController {
    private final RecentAuthenticationService recent;
    private final CurrentUserIdProvider identity;
    @PostMapping(value="/api/me/reauthentications",consumes="application/json")
    public ResponseEntity<RecentAuthProofResponse> reauthenticate(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ReauthenticationRequest body){
        return ResponseEntity.ok().header("Cache-Control","no-store").body(recent.password(identity.getCurrentUserId(),UUID.fromString(jwt.getClaimAsString("sid")),RecentAuthProof.Operation.valueOf(body.getOperation()),body.getPassword()));
    }
}
