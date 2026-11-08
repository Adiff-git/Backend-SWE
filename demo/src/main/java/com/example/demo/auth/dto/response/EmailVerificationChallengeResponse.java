package com.example.demo.auth.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class EmailVerificationChallengeResponse {
    private final UUID id;
    private final String purpose;
    private final Instant expiresAt;
    private final String deliveryStatus;
}
