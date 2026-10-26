package com.example.demo.auth.service.impl;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.auth.entity.AuthenticationSession;
import com.example.demo.auth.entity.RefreshCredential;
import com.example.demo.auth.repository.AuthenticationSessionRepository;
import com.example.demo.auth.repository.RefreshCredentialRepository;
import com.example.demo.auth.exception.AuthenticationFailedException;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AuthSessionServiceImpl implements AuthSessionService {
    private final AuthenticationSessionRepository sessions;
    private final RefreshCredentialRepository credentials;
    private final jakarta.persistence.EntityManager entityManager;
    @Transactional public AuthenticationSession create(Long owner,Instant now) {
        AuthenticationSession s=new AuthenticationSession();
        s.setId(UUID.randomUUID());
        s.setUserId(owner);
        s.setCreatedAt(now);
        s.setExpiresAt(now.plusSeconds(604800));
        return sessions.saveAndFlush(s);
    }
    public Optional<AuthenticationSession> findSessionEntity(UUID id) {
        return sessions.findById(id);
    }
    @Transactional public AuthenticationSession getSessionEntityForUpdate(UUID id) {
        AuthenticationSession session=sessions.findForUpdate(id).orElseThrow(AuthenticationFailedException::new);
        // The earlier candidate read may be cached while waiting for the owner lock.
        entityManager.refresh(session,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return session;
    }
    public Optional<RefreshCredential> findCredentialEntity(byte[] hash) {
        return credentials.findByTokenHash(hash);
    }
    @Transactional public RefreshCredential getCredentialEntityForUpdate(Long id) {
        RefreshCredential credential=credentials.findForUpdate(id).orElseThrow(AuthenticationFailedException::new);
        // Re-read consumed state after a competing rotation commits.
        entityManager.refresh(credential,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return credential;
    }
    @Transactional public void createCredential(AuthenticationSession session,byte[] hash,Instant now) {
        RefreshCredential r=new RefreshCredential();
        r.setSessionId(session.getId());
        r.setTokenHash(hash);
        r.setIssuedAt(now);
        credentials.saveAndFlush(r);
    }
    @Transactional public void consume(RefreshCredential credential,Instant now) {
        credential.setConsumedAt(now);
        credentials.flush();
    }
    @Transactional public void revoke(AuthenticationSession session,Instant now,String reason) {
        session.revoke(now,reason);
        sessions.flush();
    }
    @Transactional public void revokeAll(Long owner,Instant now) {
        for(AuthenticationSession s:sessions.findByUserIdOrderById(owner))s.revoke(now,"PASSWORD_CHANGED");
        sessions.flush();
    }
    public List<Long> findExpiredOwners(Instant deadline,int limit) {
        return sessions.findExpiredOwnerIds(deadline,limit);
    }
    @Transactional public void purgeExpired(Long owner,Instant deadline) {
        for(AuthenticationSession s:sessions.findTop100ByUserIdAndExpiresAtLessThanOrderById(owner,deadline)) {
            credentials.deleteAll(credentials.findBySessionIdOrderById(s.getId()));
            credentials.flush();
            sessions.delete(s);
        }
        sessions.flush();
    }
}
