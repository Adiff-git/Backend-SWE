package com.example.demo.auth.service.impl;

import com.example.demo.auth.entity.GoogleIdentity;
import com.example.demo.auth.entity.OAuthHandoff;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.repository.GoogleIdentityRepository;
import com.example.demo.auth.service.GoogleExchangeOutcome;
import com.example.demo.auth.service.GoogleHandoffService;
import com.example.demo.auth.service.GoogleLoginService;
import com.example.demo.auth.service.IssuedTokens;
import com.example.demo.auth.service.VerifiedAccountSessionService;
import com.example.demo.auth.validator.PendingRegistrationValidator;
import com.example.demo.user.dto.request.UpdateUserRequest;
import com.example.demo.user.service.UserService;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
@ConditionalOnProperty(name="security.identity.enabled",havingValue="true")
public class GoogleLoginServiceImpl implements GoogleLoginService {
    private final GoogleIdentityRepository identities;
    private final GoogleHandoffService handoffs;
    private final VerifiedAccountSessionService accounts;
    private final UserService users;
    private final PendingRegistrationValidator validator;
    private final Clock clock;
    public OAuthHandoff.Purpose purpose(){return OAuthHandoff.Purpose.LOGIN;}
    @Transactional(propagation=Propagation.MANDATORY)
    public GoogleExchangeOutcome exchange(UUID handoffId,byte[] hash,String verifier,Jwt ignored){
        OAuthHandoff hint=handoffs.getHint(handoffId);GoogleIdentity identity=find(hint);
        if(identity!=null)users.getUserEntityForUpdate(identity.getUserId());
        OAuthHandoff handoff=handoffs.lockForRedemption(handoffId,hash,verifier);
        if(handoff.getPurpose()!=purpose())throw invalid();
        IssuedTokens tokens;
        if(identity==null){
            if(!handoff.isProviderEmailVerified())throw new IdentityException(400,"GOOGLE_EMAIL_REQUIRED","Google must provide a verified email address");
            String email=validator.normalizeEmail(handoff.getProviderEmail());
            if(users.resolveSecurityIdentifier(email).isPresent())throw new IdentityException(409,"ACCOUNT_CONNECTION_REQUIRED","Sign in to the existing account and use Connect Google");
            tokens=accounts.create(email,null);Long owner=users.resolveSecurityIdentifier(email).orElseThrow();
            identity=new GoogleIdentity();identity.setUserId(owner);identity.setProvider("GOOGLE");identity.setIssuer(handoff.getValidatedIssuer());identity.setSubject(handoff.getSubject());
            identity.setProviderEmail(email);identity.setLinkedAt(clock.instant());
            if(handoff.getInitialDisplayName()!=null){UpdateUserRequest profile=new UpdateUserRequest();profile.setDisplayName(handoff.getInitialDisplayName());users.updateUser(owner,profile);}
        }else{
            GoogleIdentity current=find(handoff);if(current==null || !current.getId().equals(identity.getId()))throw invalid();
            tokens=accounts.issueForExisting(identity.getUserId());
        }
        identity.setLastSuccessfulLoginAt(clock.instant());identities.saveAndFlush(identity);handoffs.consume(handoff);
        return GoogleExchangeOutcome.login(tokens);
    }
    private GoogleIdentity find(OAuthHandoff handoff){return identities.findByProviderAndIssuerAndSubject("GOOGLE",handoff.getValidatedIssuer(),handoff.getSubject()).orElse(null);}
    private IdentityException invalid(){return new IdentityException(401,"HANDOFF_INVALID","Google authentication must be restarted");}
}
