package com.example.demo.auth.service.impl;

import com.example.demo.auth.repository.IdentityCleanupRepository;
import com.example.demo.auth.service.IdentityCleanupService;
import com.example.demo.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service @RequiredArgsConstructor
public class IdentityCleanupServiceImpl implements IdentityCleanupService {
    private final IdentityCleanupRepository repository;
    private final UserService users;
    private final PlatformTransactionManager transactions;
    private final Clock clock;
    private final com.example.demo.auth.service.IdentityAuditService audit;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(IdentityCleanupServiceImpl.class);
    public boolean purgeOverdueMail(){
        TransactionTemplate tx=new TransactionTemplate(transactions);Instant now=clock.instant();
        tx.executeWithoutResult(status->repository.eraseExpiredMail(now));
        boolean backlog=repository.hasExpiredMail(now);
        if(backlog){audit.record(com.example.demo.auth.service.IdentityAuditService.Event.CLEANUP_BACKLOG);LOG.warn("Identity expired-mail cleanup backlog remains");}return !backlog;
    }
    public void cleanup(){
        purgeOverdueMail();Instant cutoff=clock.instant().minusSeconds(86400);TransactionTemplate tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status->repository.deleteExpiredHandoffs(cutoff));
        for(UUID id:repository.expiredAttempts(cutoff))tx.executeWithoutResult(status->repository.deleteAttempt(id));
        tx.executeWithoutResult(status->repository.deleteExpiredProofs(cutoff));
        tx.executeWithoutResult(status->repository.deleteEmptySlots(cutoff));
        for(UUID id:repository.expiredPending(cutoff))tx.executeWithoutResult(status->repository.deletePending(id,clock.instant()));
        for(Long owner:repository.expiredChallengeOwners(cutoff))tx.executeWithoutResult(status->{users.getUserEntityForUpdate(owner);repository.deleteUserChallenges(owner,cutoff,clock.instant());});
        audit.record(com.example.demo.auth.service.IdentityAuditService.Event.CLEANUP_BATCH);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void beforeSessionDeletion(UUID session,Instant now){
        // Caller holds the user owner lock. Dependent short-lived state cannot outlive a seven-day session.
        for(UUID attempt:repository.sessionAttempts(session))repository.deleteAttempt(attempt);
        repository.deleteSessionProofs(session);
    }
}
