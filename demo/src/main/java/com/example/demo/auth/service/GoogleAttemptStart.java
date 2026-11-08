package com.example.demo.auth.service;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Transient secrets; never serialize or log this object. */
@Getter
@AllArgsConstructor
public class GoogleAttemptStart {
    private final String ticket;
    private final String cookie;
    private final Instant expiresAt;
}
