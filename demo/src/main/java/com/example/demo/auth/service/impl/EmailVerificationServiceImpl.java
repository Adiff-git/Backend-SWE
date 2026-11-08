package com.example.demo.auth.service.impl;

import com.example.demo.auth.config.VerificationProperties;
import com.example.demo.auth.converter.EmailVerificationConverter;
import com.example.demo.auth.dto.response.EmailVerificationChallengeResponse;
import com.example.demo.auth.dto.response.VerifiedEmailResponse;
import com.example.demo.auth.entity.EmailVerificationChallenge;
import com.example.demo.auth.entity.RecentAuthProof;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.repository.EmailVerificationChallengeRepository;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.auth.service.EmailVerificationService;
import com.example.demo.auth.service.IdentityCommandOutcome;
import com.example.demo.auth.service.IdentityCommandService;
import com.example.demo.auth.service.IdentityProofService;
import com.example.demo.auth.service.RecentAuthenticationService;
import com.example.demo.auth.service.VerificationMailService;
import com.example.demo.auth.validator.PendingRegistrationValidator;
import com.example.demo.user.entity.User;
import com.example.demo.user.enums.AccountStatus;
import com.example.demo.user.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
@ConditionalOnProperty(name="security.verification.enabled",havingValue="true")
public class EmailVerificationServiceImpl implements EmailVerificationService {
    private final EmailVerificationChallengeRepository challenges;
    private final UserService users;
    private final IdentityProofService proofs;
    private final IdentityCommandService commands;
    private final VerificationMailService mail;
    private final RecentAuthenticationService recent;
    private final AuthSessionService sessions;
    private final PendingRegistrationValidator validator;
    private final VerificationProperties properties;
    private final EmailVerificationConverter converter;
    private final EntityManager entities;
    private final Clock clock;
    public EmailVerificationChallengeResponse create(Long owner,UUID sid,EmailVerificationChallenge.Purpose purpose,String destination){
        com.example.demo.auth.service.IdentityEntryBoundary.require();
        if(purpose==null)throw invalid();String normalized=validator.normalizeEmail(destination);
        return commands.<EmailVerificationChallengeResponse>execute(()->{
                User user=users.getUserEntityForUpdate(owner);
                if(purpose==EmailVerificationChallenge.Purpose.REGISTRATION){
                    if(user.getEmail()==null || !normalized.equals(user.getEmail()))throw invalid();
                    if(user.getEmailVerifiedAt()!=null)throw new IdentityException(409,"EMAIL_ALREADY_VERIFIED","Email is already verified");
                }else if(normalized.equals(user.getEmail()) || users.resolveSecurityIdentifier(normalized).isPresent())throw conflict();
                Instant now=clock.instant();EmailVerificationChallenge previous=challenges.findByUserIdAndPurposeAndActiveTrue(owner,purpose).orElse(null);
                if(previous!=null){
                    previous=lock(previous.getId());previous.setActive(false);previous.setCodeMac(null);previous.setSupersededAt(now);challenges.flush();
                }
                requireSession(owner,sid);if(previous!=null)mail.supersede(previous.getId());
                EmailVerificationChallenge challenge=new EmailVerificationChallenge();challenge.setId(UUID.randomUUID());challenge.setUserId(owner);challenge.setGeneration(previous==null?1:Math.addExact(previous.getGeneration(),1));
                challenge.setDestination(normalized);challenge.setPurpose(purpose);challenge.setKeyId(properties.getKeyId());challenge.setCreatedAt(now);challenge.setExpiresAt(now.plusSeconds(600));
                challenge.setActive(true);challenge.setDeliveryStatus(EmailVerificationChallenge.DeliveryStatus.QUEUED);String code=proofs.newCode();
                challenge.setCodeMac(proofs.codeMac(challenge.getId(),"USER",owner.toString(),normalized,purpose.name(),code));challenges.saveAndFlush(challenge);
                mail.enqueue(challenge,code);
                return IdentityCommandOutcome.accepted(converter.toResponse(challenge));
            }).getValue();
    }
    @Transactional(readOnly=true)
    public EmailVerificationChallengeResponse get(Long owner,UUID id){
        EmailVerificationChallenge challenge=challenges.findById(id).filter(c->owner.equals(c.getUserId())).orElseThrow(this::notFound);
        return converter.toResponse(challenge);
    }
    public VerifiedEmailResponse confirm(Long owner,UUID sid,UUID id,String code,String secret){
        com.example.demo.auth.service.IdentityEntryBoundary.require();
        if(code==null || !code.matches("[0-9]{6}"))throw invalid();
        EmailVerificationChallenge hint=challenges.findById(id).filter(c->owner.equals(c.getUserId())).orElseThrow(this::notFound);
        try{
            IdentityCommandOutcome<VerifiedEmailResponse> result=commands.execute(()->{
                User user=users.getUserEntityForUpdate(owner);
                RecentAuthProof proof=hint.getPurpose()==EmailVerificationChallenge.Purpose.EMAIL_CHANGE?recent.lockProof(owner,sid,RecentAuthProof.Operation.EMAIL_CHANGE,secret):null;
                EmailVerificationChallenge challenge=lock(id);if(!owner.equals(challenge.getUserId()))throw notFound();
                if(!challenge.isActive() || !clock.instant().isBefore(challenge.getExpiresAt()))throw invalid();
                requireSession(owner,sid);
                if(!properties.getKeyId().equals(challenge.getKeyId()) || !proofs.matches(challenge.getCodeMac(),proofs.codeMac(id,"USER",owner.toString(),challenge.getDestination(),challenge.getPurpose().name(),code))){

                    challenges.flush();return IdentityCommandOutcome.rejected("VERIFICATION_FAILED");
                }
                if(challenge.getPurpose()==EmailVerificationChallenge.Purpose.REGISTRATION && (!challenge.getDestination().equals(user.getEmail()) || user.getEmailVerifiedAt()!=null))throw invalid();
                users.replaceVerifiedEmail(owner,challenge.getDestination(),clock.instant());if(proof!=null)recent.consume(proof);
                challenge.setActive(false);challenge.setCodeMac(null);challenge.setConsumedAt(clock.instant());mail.supersede(id);challenges.flush();
                return IdentityCommandOutcome.accepted(new VerifiedEmailResponse(challenge.getDestination(),true));
            });
            if(!result.isAccepted())throw invalid();return result.getValue();
        }catch(DataIntegrityViolationException collision){throw conflict();}
    }
    private EmailVerificationChallenge lock(UUID id){EmailVerificationChallenge challenge=challenges.findForUpdate(id).orElseThrow(this::notFound);entities.refresh(challenge,LockModeType.PESSIMISTIC_WRITE);return challenge;}
    private void requireSession(Long owner,UUID sid){
        com.example.demo.auth.entity.AuthenticationSession session=sessions.getSessionEntityForUpdate(sid);
        if(!owner.equals(session.getUserId()) || !session.isActiveAt(clock.instant()) || users.getUserSecuritySnapshot(owner).getAccountStatus()!=AccountStatus.ACTIVE)throw new IdentityException(401,"AUTHENTICATION_FAILED","Authentication failed");
    }
    private IdentityException notFound(){return new IdentityException(404,"CHALLENGE_NOT_FOUND","Verification challenge was not found");}
    private IdentityException invalid(){return new IdentityException(400,"VERIFICATION_FAILED","Email verification failed");}
    private IdentityException conflict(){return new IdentityException(409,"EMAIL_UNAVAILABLE","Email is unavailable");}
}
