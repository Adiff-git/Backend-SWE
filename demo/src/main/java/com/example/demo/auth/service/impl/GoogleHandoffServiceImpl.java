package com.example.demo.auth.service.impl;

import com.example.demo.auth.entity.OAuthHandoff;
import com.example.demo.auth.entity.OAuthLoginAttempt;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.repository.OAuthBrowserSlotRepository;
import com.example.demo.auth.repository.OAuthHandoffRepository;
import com.example.demo.auth.repository.OAuthLoginAttemptRepository;
import com.example.demo.auth.service.GoogleHandoffService;
import com.example.demo.auth.service.IdentityProofService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
@ConditionalOnProperty(name="security.identity.enabled",havingValue="true")
@Transactional
public class GoogleHandoffServiceImpl implements GoogleHandoffService {
    private final OAuthHandoffRepository handoffs;
    private final OAuthBrowserSlotRepository slots;
    private final OAuthLoginAttemptRepository attempts;
    private final IdentityProofService proofs;
    private final EntityManager entities;
    private final Clock clock;
    public OAuthHandoff getHint(UUID id){return handoffs.findById(id).orElseThrow(this::invalid);}
    public OAuthHandoff lockForRedemption(UUID id,byte[] hash,String verifier) {
        OAuthHandoff handoff=handoffs.findForUpdate(id).orElseThrow(this::invalid);entities.refresh(handoff,LockModeType.PESSIMISTIC_WRITE);
        if(verifier==null || !verifier.matches("[A-Za-z0-9._~-]{43,128}") || handoff.getConsumedAt()!=null || !clock.instant().isBefore(handoff.getExpiresAt()) || !proofs.matches(hash,handoff.getCodeHash()))throw invalid();
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(proofs.hash(verifier));
        if(!proofs.matches(challenge.getBytes(java.nio.charset.StandardCharsets.US_ASCII),handoff.getPkceChallenge().getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw invalid();
        if(slots.findById(handoff.getBrowserSlotId()).filter(s->s.getGeneration()==handoff.getBrowserGeneration()).isEmpty()
            || attempts.findById(handoff.getAttemptId()).filter(a->a.getStatus()==OAuthLoginAttempt.Status.CALLBACK_VALIDATED).isEmpty())throw invalid();
        return handoff;
    }
    public void consume(OAuthHandoff handoff){
        com.example.demo.auth.entity.OAuthBrowserSlot slot=slots.findForUpdate(handoff.getBrowserSlotId()).orElseThrow(this::invalid);entities.refresh(slot,LockModeType.PESSIMISTIC_WRITE);
        if(slot.getGeneration()!=handoff.getBrowserGeneration())throw invalid();
        handoff.setConsumedAt(clock.instant());handoff.setCodeHash(null);handoffs.flush();
    }
    private IdentityException invalid(){return new IdentityException(401,"HANDOFF_INVALID","Google authentication must be restarted");}
}
