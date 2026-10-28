package com.example.demo.auth.controller;
import com.example.demo.auth.service.AuthService;
import com.example.demo.auth.service.IssuedTokens;
import com.example.demo.auth.converter.AuthConverter;
import com.example.demo.auth.dto.request.RegisterRequest;
import com.example.demo.auth.dto.request.LoginRequest;
import com.example.demo.auth.dto.request.RefreshRequest;
import com.example.demo.auth.dto.request.LogoutRequest;
import com.example.demo.auth.dto.request.ChangePasswordRequest;
import com.example.demo.auth.dto.response.TokenResponse;
import com.example.demo.common.identity.CurrentUserIdProvider;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final AuthConverter converter;
    private final CurrentUserIdProvider identity;
    @PostMapping("/register") public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return response(201,auth.register(request.getUsername(),request.getPassword()));
    }
    @PostMapping("/login") public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return response(200,auth.login(request.getIdentifier(),request.getPassword()));
    }
    @PostMapping("/refresh") public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return response(200,auth.refresh(request.getRefreshToken()));
    }
    @PostMapping("/logout") public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        auth.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/password") public ResponseEntity<Void> password(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(identity.getCurrentUserId(),UUID.fromString(jwt.getClaimAsString("sid")),request.getCurrentPassword(),request.getNewPassword());
        return ResponseEntity.noContent().build();
    }
    private ResponseEntity<TokenResponse> response(int status,IssuedTokens tokens) {
        return ResponseEntity.status(status).header("Authorization","Bearer "+tokens.getAccessToken()).header("Cache-Control","no-store").body(converter.toResponse(tokens));
    }
}
