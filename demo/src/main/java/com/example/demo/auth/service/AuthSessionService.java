package com.example.demo.auth.service;
import com.example.demo.auth.entity.AuthenticationSession;
import com.example.demo.auth.entity.RefreshCredential;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
public interface AuthSessionService {
    AuthenticationSession create(Long userId,Instant now);
    Optional<AuthenticationSession> findSessionEntity(UUID id);
    AuthenticationSession getSessionEntityForUpdate(UUID id);
    Optional<RefreshCredential> findCredentialEntity(byte[] hash);
    RefreshCredential getCredentialEntityForUpdate(Long id);
    void createCredential(AuthenticationSession session,byte[] hash,Instant now);
    void consume(RefreshCredential credential,Instant now);
    void revoke(AuthenticationSession session,Instant now,String reason);
    void revokeAll(Long userId,Instant now);
    List<Long> findExpiredOwners(Instant deadline,int limit);
    void purgeExpired(Long owner,Instant deadline);
}
