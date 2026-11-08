package com.example.demo.auth.service.impl;

import com.example.demo.auth.dto.response.GoogleConnectionResponse;
import com.example.demo.auth.entity.AuthenticationSession;
import com.example.demo.auth.entity.GoogleIdentity;
import com.example.demo.auth.entity.OAuthHandoff;
import com.example.demo.auth.entity.RecentAuthProof;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.repository.GoogleIdentityRepository;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.auth.service.GoogleConnectionService;
import com.example.demo.auth.service.GoogleExchangeOutcome;
import com.example.demo.auth.service.GoogleHandoffService;
import com.example.demo.auth.service.RecentAuthenticationService;
import com.example.demo.user.enums.AccountStatus;
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
public class GoogleConnectionServiceImpl implements GoogleConnectionService {
    private final GoogleIdentityRepository identities;
    private final GoogleHandoffService handoffs;
    private final UserService users;
    private final AuthSessionService sessions;
    private final RecentAuthenticationService recent;
    private final com.example.demo.auth.repository.RecentAuthProofRepository proofRepository;
    private final jakarta.persistence.EntityManager entities;
    private final Clock clock;
    public OAuthHandoff.Purpose purpose(){return OAuthHandoff.Purpose.LINK;}
    @Transactional(readOnly=true)
    public GoogleConnectionResponse getConnection(Long owner){
        return identities.findByUserIdAndProvider(owner,"GOOGLE").map(identity->new GoogleConnectionResponse(true,identity.getLinkedAt())).orElseGet(()->new GoogleConnectionResponse(false,null));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public GoogleExchangeOutcome exchange(UUID id,byte[] hash,String verifier,Jwt bearer){
        OAuthHandoff hint=handoffs.getHint(id);Long owner=hint.getOwnerUserId();UUID sid=hint.getInitiatingSessionId();
        if(bearer==null || owner==null || !owner.toString().equals(bearer.getSubject()) || !sid.toString().equals(bearer.getClaimAsString("sid")))throw invalid();
        users.getUserEntityForUpdate(owner);
        if(!users.getUserSecuritySnapshot(owner).getAuthorities().contains("MANAGE_USER_PROFILE"))throw new IdentityException(403,"ACCOUNT_OPERATION_FORBIDDEN","Profile permission is required");
        if(hint.getRecentAuthProofId()==null)throw invalid();
        RecentAuthProof proof=proofRepository.findForUpdate(hint.getRecentAuthProofId()).orElseThrow(this::invalid);entities.refresh(proof,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        OAuthHandoff handoff=handoffs.lockForRedemption(id,hash,verifier);
        if(handoff.getPurpose()!=purpose() || handoff.getOperation()!=OAuthHandoff.Operation.CONNECT_GOOGLE || !proof.getId().equals(handoff.getRecentAuthProofId())
            || !owner.equals(proof.getOwnerUserId()) || !sid.equals(proof.getInitiatingSessionId()) || proof.getOperation()!=RecentAuthProof.Operation.CONNECT_GOOGLE
            || proof.getConsumedAt()!=null || proof.getAuthenticatedAt().isAfter(clock.instant()) || !clock.instant().isBefore(proof.getExpiresAt()))throw invalid();
        AuthenticationSession session=sessions.getSessionEntityForUpdate(sid);
        if(!owner.equals(session.getUserId()) || !session.isActiveAt(clock.instant()) || users.getUserSecuritySnapshot(owner).getAccountStatus()!=AccountStatus.ACTIVE)throw invalid();
        boolean hasPassword=users.getUserSecuritySnapshot(owner).getEncodedPassword()!=null;
        if(hasPassword && proof.getMethod()!=RecentAuthProof.Method.PASSWORD || !hasPassword && proof.getMethod()!=RecentAuthProof.Method.GOOGLE)throw invalid();
        if(proof.getMethod()==RecentAuthProof.Method.GOOGLE && identities.findById(proof.getProviderIdentityId()).filter(i->owner.equals(i.getUserId())).isEmpty())throw invalid();
        GoogleIdentity existing=identities.findByUserIdAndProvider(owner,"GOOGLE").orElse(null);
        GoogleIdentity claimed=identities.findByProviderAndIssuerAndSubject("GOOGLE",handoff.getValidatedIssuer(),handoff.getSubject()).orElse(null);
        if(existing!=null && (!existing.getIssuer().equals(handoff.getValidatedIssuer()) || !existing.getSubject().equals(handoff.getSubject())) || claimed!=null && !owner.equals(claimed.getUserId()))throw new IdentityException(409,"GOOGLE_CONNECTION_CONFLICT","Google connection is unavailable");
        if(existing==null){existing=new GoogleIdentity();existing.setUserId(owner);existing.setProvider("GOOGLE");existing.setIssuer(handoff.getValidatedIssuer());existing.setSubject(handoff.getSubject());existing.setProviderEmail(handoff.getProviderEmail());existing.setLinkedAt(clock.instant());identities.saveAndFlush(existing);}
        recent.consume(proof);handoffs.consume(handoff);return new GoogleExchangeOutcome(null,new GoogleConnectionResponse(true,existing.getLinkedAt()));
    }
    private IdentityException invalid(){return new IdentityException(401,"HANDOFF_INVALID","Google authentication must be restarted");}
}
