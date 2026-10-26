package com.example.demo.auth.service;
import java.util.UUID;
import java.time.Instant;
public interface AuthTransactionService {
    AuthOutcome register(String username,String password);
    AuthOutcome login(String identifier,String password);
    AuthOutcome refresh(String refreshToken);
    AuthOutcome logout(String refreshToken);
    AuthOutcome changePassword(Long owner,UUID sessionId,String currentPassword,String newPassword);
    void cleanupOwner(Long owner,Instant deadline);
}
