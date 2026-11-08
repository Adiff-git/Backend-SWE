package com.example.demo.auth.service.impl;

import com.example.demo.auth.config.VerificationProperties;
import com.example.demo.auth.converter.PendingRegistrationConverter;
import com.example.demo.auth.dto.response.PendingRegistrationResponse;
import com.example.demo.auth.entity.EmailVerificationChallenge;
import com.example.demo.auth.entity.PendingRegistration;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.repository.EmailVerificationChallengeRepository;
import com.example.demo.auth.repository.PendingRegistrationRepository;
import com.example.demo.auth.service.IdentityCommandOutcome;
import com.example.demo.auth.service.IdentityCommandService;
import com.example.demo.auth.service.IdentityProofService;
import com.example.demo.auth.service.IssuedTokens;
import com.example.demo.auth.service.PendingRegistrationService;
import com.example.demo.auth.service.VerificationMailService;
import com.example.demo.auth.service.VerifiedAccountSessionService;
import com.example.demo.auth.validator.PendingRegistrationValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
@ConditionalOnProperty(name="security.verification.enabled",havingValue="true")
public class PendingRegistrationServiceImpl implements PendingRegistrationService {
    private final PendingRegistrationRepository pending;
    private final EmailVerificationChallengeRepository challenges;
    private final IdentityProofService proofs;
    private final IdentityCommandService commands;
    private final VerificationMailService mail;
    private final VerifiedAccountSessionService accounts;
    private final PendingRegistrationValidator validator;
    private final PendingRegistrationConverter converter;
    private final VerificationProperties properties;
    private final PasswordEncoder passwords;
    private final EntityManager entities;
    private final Clock clock;
    public PendingRegistrationResponse initiate(String email,String password){
        com.example.demo.auth.service.IdentityEntryBoundary.require();
        String destination=validator.normalizeEmail(email);validator.password(password);
        UUID id=UUID.randomUUID();
        return commands.<PendingRegistrationResponse>execute(()->{
                Instant now=clock.instant();PendingRegistration owner=new PendingRegistration();owner.setId(id);owner.setDestination(destination);
                owner.setGeneration(1);owner.setState(PendingRegistration.State.ACTIVE);owner.setCreatedAt(now);owner.setExpiresAt(now.plusSeconds(600));
                String credential=proofs.newSecret();owner.setCredentialHash(proofs.hash(credential));pending.saveAndFlush(owner);
                return enqueue(owner,credential,now);
            }).getValue();
    }
    public PendingRegistrationResponse resend(String credential,UUID challengeId){
        com.example.demo.auth.service.IdentityEntryBoundary.require();
        validator.proof(credential,challengeId,null);
        PendingRegistration hint=pending.findByCredentialHash(proofs.hash(credential)).orElseThrow(this::invalid);
        return commands.<PendingRegistrationResponse>execute(()->{
                PendingRegistration owner=lockOwner(hint.getId(),credential);EmailVerificationChallenge previous=lockChallenge(owner,challengeId,false);
                Instant now=clock.instant();previous.setActive(false);previous.setCodeMac(null);previous.setSupersededAt(now);challenges.flush();mail.supersede(previous.getId());
                String rotated=proofs.newSecret();owner.setCredentialHash(proofs.hash(rotated));owner.setGeneration(Math.addExact(owner.getGeneration(),1));owner.setExpiresAt(now.plusSeconds(600));
                pending.flush();return enqueue(owner,rotated,now);
            }).getValue();
    }
    public IssuedTokens confirm(String credential,UUID challengeId,String code,String password){
        com.example.demo.auth.service.IdentityEntryBoundary.require();
        validator.proof(credential,challengeId,code);if(code==null)throw invalid();validator.password(password);
        PendingRegistration hint=pending.findByCredentialHash(proofs.hash(credential)).orElseThrow(this::invalid);
        IdentityCommandOutcome<IssuedTokens> result;
        try{
            result=commands.execute(()->{
                PendingRegistration owner=lockOwner(hint.getId(),credential);EmailVerificationChallenge challenge=lockChallenge(owner,challengeId);
                if(!properties.getKeyId().equals(challenge.getKeyId()) || !proofs.matches(challenge.getCodeMac(),proofs.codeMac(challengeId,"PENDING",owner.getId().toString(),owner.getDestination(),"REGISTRATION",code))){
                    challenges.flush();return IdentityCommandOutcome.rejected("REGISTRATION_VERIFICATION_FAILED");
                }
                IssuedTokens tokens=accounts.create(owner.getDestination(),passwords.encode(password));
                Instant now=clock.instant();challenge.setActive(false);challenge.setConsumedAt(now);challenge.setCodeMac(null);
                owner.setState(PendingRegistration.State.CONFIRMED);owner.setConsumedAt(now);owner.setCredentialHash(null);
                mail.supersede(challengeId);challenges.flush();pending.flush();
                return IdentityCommandOutcome.accepted(tokens);
            });
        }catch(DataIntegrityViolationException conflict){throw new IdentityException(409,"REGISTRATION_CONFLICT","This address cannot be registered; sign in to the existing account");}
        if(!result.isAccepted())throw invalid();return result.getValue();
    }
    private IdentityCommandOutcome<PendingRegistrationResponse> enqueue(PendingRegistration owner,String credential,Instant now){
        EmailVerificationChallenge challenge=new EmailVerificationChallenge();challenge.setId(UUID.randomUUID());challenge.setPendingRegistrationId(owner.getId());
        challenge.setGeneration(owner.getGeneration());challenge.setDestination(owner.getDestination());challenge.setPurpose(EmailVerificationChallenge.Purpose.REGISTRATION);
        challenge.setKeyId(properties.getKeyId());challenge.setCreatedAt(now);challenge.setExpiresAt(now.plusSeconds(600));challenge.setActive(true);challenge.setDeliveryStatus(EmailVerificationChallenge.DeliveryStatus.QUEUED);
        String code=proofs.newCode();challenge.setCodeMac(proofs.codeMac(challenge.getId(),"PENDING",owner.getId().toString(),owner.getDestination(),"REGISTRATION",code));
        challenges.saveAndFlush(challenge);mail.enqueue(challenge,code);
        return IdentityCommandOutcome.accepted(converter.toResponse(credential,challenge));
    }
    private PendingRegistration lockOwner(UUID id,String credential){
        PendingRegistration owner=pending.findForUpdate(id).orElseThrow(this::invalid);entities.refresh(owner,LockModeType.PESSIMISTIC_WRITE);
        if(owner.getState()!=PendingRegistration.State.ACTIVE || !proofs.matches(owner.getCredentialHash(),proofs.hash(credential)) || !clock.instant().isBefore(owner.getExpiresAt()))throw invalid();
        return owner;
    }
    private EmailVerificationChallenge lockChallenge(PendingRegistration owner,UUID id){return lockChallenge(owner,id,true);}
    private EmailVerificationChallenge lockChallenge(PendingRegistration owner,UUID id,boolean requireActive){
        EmailVerificationChallenge challenge=challenges.findForUpdate(id).orElseThrow(this::invalid);entities.refresh(challenge,LockModeType.PESSIMISTIC_WRITE);
        if(!owner.getId().equals(challenge.getPendingRegistrationId()) || challenge.getGeneration()!=owner.getGeneration() || requireActive && !challenge.isActive() || challenge.getConsumedAt()!=null || challenge.getSupersededAt()!=null
            || challenge.getPurpose()!=EmailVerificationChallenge.Purpose.REGISTRATION || !clock.instant().isBefore(challenge.getExpiresAt()))throw invalid();
        return challenge;
    }
    private IdentityException invalid(){return new IdentityException(400,"REGISTRATION_VERIFICATION_FAILED","Registration verification failed");}
}
