package com.example.demo.auth.service.impl;

import com.example.demo.auth.entity.AuthenticationSession;
import com.example.demo.auth.entity.GoogleIdentity;
import com.example.demo.auth.entity.OAuthHandoff;
import com.example.demo.auth.entity.RecentAuthProof;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.repository.GoogleIdentityRepository;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.auth.service.GoogleExchangeOutcome;
import com.example.demo.auth.service.GoogleHandoffService;
import com.example.demo.auth.service.GoogleReauthenticationService;
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
public class GoogleReauthenticationServiceImpl implements GoogleReauthenticationService {
    private final GoogleIdentityRepository identities;
    private final GoogleHandoffService handoffs;
    private final UserService users;
    private final AuthSessionService sessions;
    private final RecentAuthenticationService recent;
    private final Clock clock;
    public OAuthHandoff.Purpose purpose(){return OAuthHandoff.Purpose.REAUTH;}
    @Transactional(propagation=Propagation.MANDATORY)
    public GoogleExchangeOutcome exchange(UUID id,byte[] hash,String verifier,Jwt bearer){
        OAuthHandoff hint=handoffs.getHint(id);Long owner=hint.getOwnerUserId();UUID sid=hint.getInitiatingSessionId();
        if(bearer==null || owner==null || !owner.toString().equals(bearer.getSubject()) || !sid.toString().equals(bearer.getClaimAsString("sid")))throw invalid();
        users.getUserEntityForUpdate(owner);
        if(!users.getUserSecuritySnapshot(owner).getAuthorities().contains("MANAGE_USER_PROFILE"))throw new IdentityException(403,"ACCOUNT_OPERATION_FORBIDDEN","Profile permission is required");OAuthHandoff handoff=handoffs.lockForRedemption(id,hash,verifier);
        if(handoff.getPurpose()!=purpose() || handoff.getOperation()==null)throw invalid();
        AuthenticationSession session=sessions.getSessionEntityForUpdate(sid);
        if(!owner.equals(session.getUserId()) || !session.isActiveAt(clock.instant()) || users.getUserSecuritySnapshot(owner).getAccountStatus()!=AccountStatus.ACTIVE)throw invalid();
        GoogleIdentity identity=identities.findByProviderAndIssuerAndSubject("GOOGLE",handoff.getValidatedIssuer(),handoff.getSubject()).filter(i->owner.equals(i.getUserId())).orElseThrow(this::invalid);
        if(users.getUserSecuritySnapshot(owner).getEncodedPassword()!=null){
            if(handoff.getOperation()==OAuthHandoff.Operation.INITIAL_PASSWORD)throw new IdentityException(409,"PASSWORD_ALREADY_SET","A local password is already set");
            throw new IdentityException(400,"PASSWORD_REQUIRED","Use current-password authentication");
        }
        Object result=recent.google(owner,sid,RecentAuthProof.Operation.valueOf(handoff.getOperation().name()),identity.getId(),handoff.getAuthenticatedAt());handoffs.consume(handoff);
        return new GoogleExchangeOutcome(null,result);
    }
    private IdentityException invalid(){return new IdentityException(401,"HANDOFF_INVALID","Google authentication must be restarted");}
}
