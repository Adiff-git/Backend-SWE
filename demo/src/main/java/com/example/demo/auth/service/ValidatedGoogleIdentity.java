package com.example.demo.auth.service;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ValidatedGoogleIdentity {
    private final String subject;
    private final String email;
    private final boolean emailVerified;
    private final String displayName;
    private final Instant authenticatedAt;
}
