package com.example.demo.auth.service;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

@Getter
@AllArgsConstructor
public class GoogleAuthorizationState {
    private final UUID attemptId;
    private final OAuth2AuthorizationRequest request;
}
