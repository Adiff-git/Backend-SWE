package com.example.demo.auth.service.impl;

import com.example.demo.auth.entity.OAuthHandoff;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.repository.OAuthHandoffRepository;
import com.example.demo.auth.service.GoogleExchangeHandler;
import com.example.demo.auth.service.GoogleExchangeOutcome;
import com.example.demo.auth.service.GoogleExchangeService;
import com.example.demo.auth.service.IdentityCommandOutcome;
import com.example.demo.auth.service.IdentityCommandService;
import com.example.demo.auth.service.IdentityProofService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
@ConditionalOnProperty(name="security.identity.enabled",havingValue="true")
public class GoogleExchangeServiceImpl implements GoogleExchangeService {
    private final OAuthHandoffRepository handoffs;
    private final IdentityProofService proofs;
    private final com.example.demo.auth.service.IdentityAuditService audit;
    private final IdentityCommandService commands;
    private final ObjectProvider<GoogleExchangeHandler> handlers;
    @Transactional(readOnly=true)
    public OAuthHandoff.Purpose purpose(String code) {
        if(code==null || code.length()!=43)throw invalid();
        return handoffs.findByCodeHash(proofs.hash(code)).orElseThrow(this::invalid).getPurpose();
    }
    public GoogleExchangeOutcome exchange(String code,String verifier,Jwt bearer) {
        com.example.demo.auth.service.IdentityEntryBoundary.require();
        OAuthHandoff.Purpose purpose=purpose(code);
        GoogleExchangeHandler handler=handlers.orderedStream().filter(h->h.purpose()==purpose).findFirst()
            .orElseThrow(()->new IdentityException(503,"LOGIN_UNAVAILABLE","This sign-in operation is temporarily unavailable"));
        try {
            return commands.<GoogleExchangeOutcome>execute(()->{
                GoogleExchangeOutcome value=handler.exchange(handoffs.findByCodeHash(proofs.hash(code)).orElseThrow(this::invalid).getId(),proofs.hash(code),verifier,bearer);
                audit.afterCommit(com.example.demo.auth.service.IdentityAuditService.Event.HANDOFF_ACCEPTED);
                return IdentityCommandOutcome.accepted(value);
            }).getValue();
        }catch(DataIntegrityViolationException conflict){throw new IdentityException(409,"ACCOUNT_CONNECTION_REQUIRED","Sign in to your existing account and use Connect Google, or retry a concurrent login");}
    }
    private IdentityException invalid(){return new IdentityException(401,"HANDOFF_INVALID","Google authentication must be restarted");}
}
