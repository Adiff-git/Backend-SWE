package com.example.demo.auth.service;

import java.time.Instant;
import java.util.UUID;

public interface IdentityCleanupService {
    void cleanup();
    boolean purgeOverdueMail();
    void beforeSessionDeletion(UUID session,Instant now);
}
