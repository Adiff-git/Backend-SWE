package com.example.demo.auth.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class PendingRegistrationResponse {
    private final String registrationCredential;
    private final UUID challengeId;
    private final Instant expiresAt;
}
