package com.example.demo.auth.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class RecentAuthProofResponse {
    private final String recentAuthProof;
    private final Instant expiresAt;
}
