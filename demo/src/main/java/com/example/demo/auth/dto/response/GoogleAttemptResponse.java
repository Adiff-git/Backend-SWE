package com.example.demo.auth.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GoogleAttemptResponse {
    private final String authorizationUrl;
    private final Instant expiresAt;
}
