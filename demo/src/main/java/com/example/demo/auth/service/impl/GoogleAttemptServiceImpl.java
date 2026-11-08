package com.example.demo.auth.service.impl;

import com.example.demo.auth.entity.OAuthBrowserSlot;
import com.example.demo.auth.entity.OAuthHandoff;
import com.example.demo.auth.entity.OAuthLoginAttempt;
import com.example.demo.auth.entity.RecentAuthProof;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.repository.OAuthBrowserSlotRepository;
import com.example.demo.auth.repository.OAuthHandoffRepository;
import com.example.demo.auth.repository.OAuthLoginAttemptRepository;
import com.example.demo.auth.repository.RecentAuthProofRepository;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.auth.service.GoogleAttemptService;
import com.example.demo.auth.service.GoogleAttemptStart;
import com.example.demo.auth.service.GoogleAuthorizationState;
import com.example.demo.auth.service.IdentityCommandOutcome;
import com.example.demo.auth.service.IdentityCommandService;
import com.example.demo.auth.service.IdentityProofService;
import com.example.demo.auth.service.ValidatedGoogleIdentity;
import com.example.demo.auth.validator.GoogleAttemptValidator;
import com.example.demo.user.enums.AccountStatus;
import com.example.demo.user.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name="security.identity.enabled",havingValue="true")
public class GoogleAttemptServiceImpl implements GoogleAttemptService {
    private final OAuthBrowserSlotRepository slots;
    private final OAuthLoginAttemptRepository attempts;
    private final OAuthHandoffRepository handoffs;
    private final RecentAuthProofRepository recent;
    private final IdentityProofService proofs;
    private final GoogleAttemptValidator validator;
    private final IdentityCommandService commands;
    private final UserService users;
    private final AuthSessionService sessions;
    private final Clock clock;
    private final EntityManager entities;
    private final JsonMapper json;
    private final com.example.demo.auth.service.IdentityAuditService audit;
    private final com.example.demo.auth.config.VerificationProperties verification;

    public GoogleAttemptStart start(String challenge,String method,String cookie,OAuthLoginAttempt.Purpose purpose,OAuthLoginAttempt.Operation operation,Long owner,UUID sid,String accountProof) {
        com.example.demo.auth.service.IdentityEntryBoundary.require();
        validator.validateChallenge(challenge,method);
        return commands.execute(()->{
            Instant now=clock.instant();
            RecentAuthProof local=null;
            if(purpose!=OAuthLoginAttempt.Purpose.LOGIN) {
                requireSession(owner,sid,now);
                if(purpose==OAuthLoginAttempt.Purpose.LINK) {
                    if(accountProof==null)throw invalid();
                    local=recent.findBySecretHash(proofs.hash(accountProof)).orElseThrow(this::invalid);
                    if(!owner.equals(local.getOwnerUserId()) || !sid.equals(local.getInitiatingSessionId())
                        || local.getOperation()!=RecentAuthProof.Operation.CONNECT_GOOGLE || local.getConsumedAt()!=null || !now.isBefore(local.getExpiresAt()))throw invalid();
                } else {
                    if(operation==null)throw invalid();
                    boolean password=users.getUserSecuritySnapshot(owner).getEncodedPassword()!=null;
                    if(password && operation!=OAuthLoginAttempt.Operation.INITIAL_PASSWORD)throw new IdentityException(400,"PASSWORD_REQUIRED","Use current-password authentication");
                    if(password)throw new IdentityException(409,"PASSWORD_ALREADY_SET","A local password is already set");
                }
            }
            OAuthBrowserSlot slot=null;
            UUID cookieId=cookieId(cookie);
            if(cookieId!=null) {
                slot=slots.findForUpdate(cookieId).orElse(null);
                if(slot!=null) {
                    entities.refresh(slot,LockModeType.PESSIMISTIC_WRITE);
                    if(!now.isBefore(slot.getExpiresAt()) || !proofs.matches(slot.getSlotSecretHash(),proofs.hash(cookie))
                        && !attempts.existsByBrowserSlotIdAndBrowserBindingHashAndExpiresAtAfter(slot.getId(),proofs.hash(cookie),now))slot=null;
                }
            }
            if(slot==null) {
                slot=new OAuthBrowserSlot();slot.setId(UUID.randomUUID());slot.setGeneration(1);
            } else {
                if(slot.getCurrentAttemptId()!=null)attempts.findById(slot.getCurrentAttemptId()).ifPresent(a->a.setStatus(OAuthLoginAttempt.Status.FAILED));
                slot.setGeneration(Math.addExact(slot.getGeneration(),1));
            }
            String cookieValue=slot.getId()+"."+proofs.newSecret();
            slot.setSlotSecretHash(proofs.hash(cookieValue));slot.setExpiresAt(now.plusSeconds(300));
            slot=slots.saveAndFlush(slot);
            String ticket=proofs.newSecret();
            OAuthLoginAttempt attempt=new OAuthLoginAttempt();attempt.setId(UUID.randomUUID());
            attempt.setStartTicketHash(proofs.hash(ticket));attempt.setPurpose(purpose);attempt.setOperation(operation);
            attempt.setOwnerUserId(owner);attempt.setInitiatingSessionId(sid);attempt.setRecentAuthProofId(local==null?null:local.getId());
            attempt.setDestinationKey("default");attempt.setPkceChallenge(challenge);attempt.setBrowserSlotId(slot.getId());
            attempt.setBrowserGeneration(slot.getGeneration());attempt.setBrowserBindingHash(proofs.hash(cookieValue));
            attempt.setStatus(OAuthLoginAttempt.Status.CREATED);attempt.setCreatedAt(now);attempt.setExpiresAt(now.plusSeconds(300));
            attempt=attempts.saveAndFlush(attempt);
            slot.setCurrentAttemptId(attempt.getId());slots.flush();
            return IdentityCommandOutcome.accepted(new GoogleAttemptStart(ticket,cookieValue,attempt.getExpiresAt()));
        }).getValue();
    }
    @Transactional(readOnly=true)
    public OAuthLoginAttempt.Purpose authorizationPurpose(String ticket,String cookie) { return ticket(ticket,cookie,false).getPurpose(); }
    @Transactional
    public void saveAuthorization(String ticket,String cookie,OAuth2AuthorizationRequest request) {
        OAuthLoginAttempt attempt=ticket(ticket,cookie,true);
        Map<String,Object> state=new LinkedHashMap<>();
        state.put("uri",request.getAuthorizationUri());state.put("client",request.getClientId());state.put("redirect",request.getRedirectUri());
        state.put("scopes",request.getScopes());state.put("state",request.getState());
        state.put("additional",scalarMap(request.getAdditionalParameters()));state.put("attributes",scalarMap(request.getAttributes()));
        attempt.setEncryptedRequest(proofs.encrypt(json.writeValueAsBytes(state),attempt.getId().toString()));
        attempt.setEncryptionKeyId(verification.getKeyId());attempt.setOauthStateHash(proofs.hash(request.getState()));
        attempt.setStatus(OAuthLoginAttempt.Status.STARTED);attempts.flush();
    }
    @Transactional
    public GoogleAuthorizationState loadAuthorization(String state,String cookie,boolean consume) {
        if(state==null)return null;
        OAuthLoginAttempt hint=attempts.findByOauthStateHash(proofs.hash(state)).orElse(null);
        if(hint==null)return null;
        OAuthLoginAttempt attempt=consume?lockAttempt(hint.getId()):hint;

        if(attempt.getStatus()!=OAuthLoginAttempt.Status.STARTED || attempt.getConsumedAt()!=null)return null;
        if(!clock.instant().isBefore(attempt.getExpiresAt()) || cookie==null || !correlated(attempt,cookie) || attempt.getEncryptedRequest()==null){
            if(consume){attempt.setStatus(OAuthLoginAttempt.Status.FAILED);attempt.setConsumedAt(clock.instant());attempt.setEncryptedRequest(null);attempts.flush();}
            return null;
        }
        OAuth2AuthorizationRequest request=decode(attempt);
        if(consume){attempt.setConsumedAt(clock.instant());attempt.setEncryptedRequest(null);attempts.flush();}
        return new GoogleAuthorizationState(attempt.getId(),request);
    }
    @Transactional
    public String complete(UUID id,ValidatedGoogleIdentity identity) {
        OAuthLoginAttempt attempt=lockAttempt(id);
        OAuthBrowserSlot slot=slots.findById(attempt.getBrowserSlotId()).orElseThrow(this::invalid);
        Instant now=clock.instant();
        if(attempt.getStatus()!=OAuthLoginAttempt.Status.STARTED || attempt.getConsumedAt()==null || slot.getGeneration()!=attempt.getBrowserGeneration()
            || !now.isBefore(attempt.getExpiresAt()) || identity.getSubject()==null || identity.getSubject().isBlank())throw invalid();
        if(attempt.getPurpose()==OAuthLoginAttempt.Purpose.REAUTH && (identity.getAuthenticatedAt()==null || identity.getAuthenticatedAt().isAfter(now)
            || !now.isBefore(identity.getAuthenticatedAt().plusSeconds(300))))throw invalid();
        String code=proofs.newSecret();OAuthHandoff handoff=new OAuthHandoff();handoff.setId(UUID.randomUUID());
        handoff.setCodeHash(proofs.hash(code));handoff.setAttemptId(id);handoff.setPurpose(OAuthHandoff.Purpose.valueOf(attempt.getPurpose().name()));
        handoff.setOperation(attempt.getOperation()==null?null:OAuthHandoff.Operation.valueOf(attempt.getOperation().name()));
        handoff.setValidatedIssuer("https://accounts.google.com");handoff.setSubject(identity.getSubject());
        handoff.setProviderEmail(identity.getEmail()!=null && identity.getEmail().length()<=254?identity.getEmail():null);
        handoff.setProviderEmailVerified(identity.isEmailVerified());String name=identity.getDisplayName();
        handoff.setInitialDisplayName(name==null || name.isBlank()?null:name.substring(0,name.offsetByCodePoints(0,Math.min(100,name.codePointCount(0,name.length())))));
        handoff.setOwnerUserId(attempt.getOwnerUserId());handoff.setInitiatingSessionId(attempt.getInitiatingSessionId());handoff.setRecentAuthProofId(attempt.getRecentAuthProofId());
        handoff.setBrowserSlotId(slot.getId());handoff.setBrowserGeneration(slot.getGeneration());handoff.setPkceChallenge(attempt.getPkceChallenge());
        handoff.setAuthenticatedAt(identity.getAuthenticatedAt());handoff.setCreatedAt(now);handoff.setExpiresAt(now.plusSeconds(60));
        audit.afterCommit(com.example.demo.auth.service.IdentityAuditService.Event.CALLBACK_ACCEPTED);
        handoffs.saveAndFlush(handoff);attempt.setStatus(OAuthLoginAttempt.Status.CALLBACK_VALIDATED);attempts.flush();return code;
    }
    @Transactional
    public void fail(UUID id) { audit.afterCommit(com.example.demo.auth.service.IdentityAuditService.Event.CALLBACK_REJECTED); if(id!=null)attempts.findById(id).ifPresent(hint->{OAuthLoginAttempt a=lockAttempt(id);a.setStatus(OAuthLoginAttempt.Status.FAILED);a.setEncryptedRequest(null);}); }
    private OAuthLoginAttempt ticket(String ticket,String cookie,boolean lock) {
        if(ticket==null || cookie==null)throw invalid();
        OAuthLoginAttempt hint=attempts.findByStartTicketHash(proofs.hash(ticket)).orElseThrow(this::invalid);
        OAuthLoginAttempt attempt=lock?lockAttempt(hint.getId()):hint;

        if(attempt.getStatus()!=OAuthLoginAttempt.Status.CREATED || !clock.instant().isBefore(attempt.getExpiresAt()) || !correlated(attempt,cookie))throw invalid();
        return attempt;
    }
    private OAuthLoginAttempt lockAttempt(UUID id){
        OAuthLoginAttempt hint=attempts.findById(id).orElseThrow(this::invalid);
        OAuthBrowserSlot slot=slots.findForUpdate(hint.getBrowserSlotId()).orElseThrow(this::invalid);entities.refresh(slot,LockModeType.PESSIMISTIC_WRITE);
        OAuthLoginAttempt attempt=attempts.findForUpdate(id).orElseThrow(this::invalid);entities.refresh(attempt,LockModeType.PESSIMISTIC_WRITE);return attempt;
    }
    private boolean correlated(OAuthLoginAttempt attempt,String cookie) {
        UUID id=cookieId(cookie);if(!attempt.getBrowserSlotId().equals(id))return false;
        OAuthBrowserSlot slot=slots.findById(id).orElse(null);
        return slot!=null && slot.getGeneration()==attempt.getBrowserGeneration() && clock.instant().isBefore(slot.getExpiresAt())
            && proofs.matches(slot.getSlotSecretHash(),proofs.hash(cookie)) && proofs.matches(attempt.getBrowserBindingHash(),proofs.hash(cookie));
    }
    private UUID cookieId(String cookie) {
        if(cookie==null || !cookie.matches("[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}"))return null;
        try{return UUID.fromString(cookie.substring(0,36));}catch(IllegalArgumentException invalid){return null;}
    }
    private void requireSession(Long owner,UUID sid,Instant now) {
        if(owner==null || sid==null || users.getUserSecuritySnapshot(owner).getAccountStatus()!=AccountStatus.ACTIVE)throw invalid();
        com.example.demo.auth.entity.AuthenticationSession session=sessions.findSessionEntity(sid).orElseThrow(this::invalid);
        if(!owner.equals(session.getUserId()) || !session.isActiveAt(now))throw invalid();
    }
    private Map<String,Object> scalarMap(Map<String,Object> values) {
        Map<String,Object> result=new LinkedHashMap<>();
        values.forEach((key,value)->{if(!(value instanceof String || value instanceof Number || value instanceof Boolean))throw invalid();result.put(key,value);});return result;
    }
    @SuppressWarnings("unchecked")
    private OAuth2AuthorizationRequest decode(OAuthLoginAttempt attempt) {
        if(!verification.getKeyId().equals(attempt.getEncryptionKeyId()))throw invalid();
        Map<String,Object> state=json.readValue(proofs.decrypt(attempt.getEncryptedRequest(),attempt.getId().toString()),Map.class);
        Set<String> scopes=new LinkedHashSet<>();for(Object scope:(List<?>)state.get("scopes"))scopes.add((String)scope);
        return OAuth2AuthorizationRequest.authorizationCode().authorizationUri((String)state.get("uri")).clientId((String)state.get("client"))
            .redirectUri((String)state.get("redirect")).scopes(scopes).state((String)state.get("state"))
            .additionalParameters(scalarMap((Map<String,Object>)state.get("additional"))).attributes(scalarMap((Map<String,Object>)state.get("attributes"))).build();
    }
    private IdentityException invalid(){return new IdentityException(401,"GOOGLE_ATTEMPT_INVALID","Google authentication must be restarted");}
}
