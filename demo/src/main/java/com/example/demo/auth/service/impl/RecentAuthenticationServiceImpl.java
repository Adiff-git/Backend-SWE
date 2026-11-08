package com.example.demo.auth.service.impl;

import com.example.demo.auth.dto.response.RecentAuthProofResponse;
import com.example.demo.auth.entity.AuthenticationSession;
import com.example.demo.auth.entity.RecentAuthProof;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.repository.RecentAuthProofRepository;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.auth.service.IdentityCommandOutcome;
import com.example.demo.auth.service.IdentityCommandService;
import com.example.demo.auth.service.IdentityProofService;
import com.example.demo.auth.service.RecentAuthenticationService;
import com.example.demo.user.enums.AccountStatus;
import com.example.demo.user.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
@ConditionalOnProperty(name="security.verification.enabled",havingValue="true")
public class RecentAuthenticationServiceImpl implements RecentAuthenticationService {
    private final RecentAuthProofRepository repository;
    private final IdentityProofService proofs;
    private final IdentityCommandService commands;
    private final UserService users;
    private final AuthSessionService sessions;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final EntityManager entities;
    private final com.example.demo.auth.service.IdentityAuditService audit;
    private static final String DUMMY="$2a$10$7EqJtq98hPqEX7fNZaFWoO5pDf7J4kL7xBBvVtJYlUXpWZJqVqQ7S";
    public RecentAuthProofResponse password(Long owner,UUID sid,RecentAuthProof.Operation operation,String password){
        if(operation==null || operation==RecentAuthProof.Operation.INITIAL_PASSWORD)throw new IdentityException(400,"OPERATION_INVALID","Unsupported authentication operation");
        IdentityCommandOutcome<RecentAuthProofResponse> result=commands.execute(()->{
                users.getUserEntityForUpdate(owner);requireSession(owner,sid);
                String encoded=users.getUserSecuritySnapshot(owner).getEncodedPassword();
                boolean valid=password!=null && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72 && passwords.matches(password,encoded==null?DUMMY:encoded);
                if(!valid || encoded==null){audit.afterCommit(com.example.demo.auth.service.IdentityAuditService.Event.PROOF_REJECTED);return IdentityCommandOutcome.rejected("AUTHENTICATION_FAILED");}
                Instant now=clock.instant();String secret=proofs.newSecret();RecentAuthProof proof=new RecentAuthProof();proof.setId(UUID.randomUUID());
                proof.setSecretHash(proofs.hash(secret));proof.setOwnerUserId(owner);proof.setInitiatingSessionId(sid);proof.setMethod(RecentAuthProof.Method.PASSWORD);proof.setOperation(operation);
                proof.setAuthenticatedAt(now);proof.setExpiresAt(now.plusSeconds(300));repository.saveAndFlush(proof);audit.afterCommit(com.example.demo.auth.service.IdentityAuditService.Event.PROOF_ACCEPTED);
                return IdentityCommandOutcome.accepted(new RecentAuthProofResponse(secret,proof.getExpiresAt()));
            });
            if(!result.isAccepted())throw invalid();return result.getValue();
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public RecentAuthProof lockProof(Long owner,UUID sid,RecentAuthProof.Operation operation,String secret){
        if(secret==null || !secret.matches("[A-Za-z0-9_-]{43}"))throw invalid();
        RecentAuthProof hint=repository.findBySecretHash(proofs.hash(secret)).orElseThrow(this::invalid);
        RecentAuthProof proof=repository.findForUpdate(hint.getId()).orElseThrow(this::invalid);entities.refresh(proof,LockModeType.PESSIMISTIC_WRITE);
        Instant now=clock.instant();
        if(!owner.equals(proof.getOwnerUserId()) || !sid.equals(proof.getInitiatingSessionId()) || operation!=proof.getOperation() || proof.getConsumedAt()!=null || !now.isBefore(proof.getExpiresAt()) || proof.getAuthenticatedAt().isAfter(now))throw invalid();
        return proof;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void consume(RecentAuthProof proof){proof.setConsumedAt(clock.instant());repository.flush();}
    @Transactional(propagation=Propagation.MANDATORY)
    public RecentAuthProofResponse google(Long owner,UUID sid,RecentAuthProof.Operation operation,Long identity,Instant authenticatedAt){
        Instant now=clock.instant();
        if(authenticatedAt==null || authenticatedAt.isAfter(now) || !now.isBefore(authenticatedAt.plusSeconds(300)))throw invalid();
        String secret=proofs.newSecret();RecentAuthProof proof=new RecentAuthProof();proof.setId(UUID.randomUUID());proof.setOwnerUserId(owner);proof.setInitiatingSessionId(sid);
        proof.setOperation(operation);proof.setMethod(RecentAuthProof.Method.GOOGLE);proof.setProviderIdentityId(identity);proof.setSecretHash(proofs.hash(secret));
        proof.setAuthenticatedAt(authenticatedAt);proof.setExpiresAt(authenticatedAt.plusSeconds(300));repository.saveAndFlush(proof);audit.afterCommit(com.example.demo.auth.service.IdentityAuditService.Event.PROOF_ACCEPTED);
        return new RecentAuthProofResponse(secret,proof.getExpiresAt());
    }
    private void requireSession(Long owner,UUID sid){
        AuthenticationSession session=sessions.getSessionEntityForUpdate(sid);
        if(!owner.equals(session.getUserId()) || !session.isActiveAt(clock.instant()) || users.getUserSecuritySnapshot(owner).getAccountStatus()!=AccountStatus.ACTIVE)throw invalid();
    }
    private IdentityException invalid(){return new IdentityException(401,"AUTHENTICATION_FAILED","Authentication failed");}
}
