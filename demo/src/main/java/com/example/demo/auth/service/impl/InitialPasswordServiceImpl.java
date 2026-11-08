package com.example.demo.auth.service.impl;

import com.example.demo.auth.entity.AuthenticationSession;
import com.example.demo.auth.entity.RecentAuthProof;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.auth.service.IdentityCommandOutcome;
import com.example.demo.auth.service.IdentityCommandService;
import com.example.demo.auth.service.InitialPasswordService;
import com.example.demo.auth.service.RecentAuthenticationService;
import com.example.demo.auth.validator.PasswordPolicyValidator;
import com.example.demo.user.enums.AccountStatus;
import com.example.demo.user.service.UserService;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
@ConditionalOnProperty(name="security.verification.enabled",havingValue="true")
public class InitialPasswordServiceImpl implements InitialPasswordService {
    private final UserService users;
    private final AuthSessionService sessions;
    private final RecentAuthenticationService recent;
    private final IdentityCommandService commands;
    private final PasswordPolicyValidator policy;
    private final PasswordEncoder passwords;
    private final com.example.demo.auth.repository.GoogleIdentityRepository identities;
    private final Clock clock;
    public void set(Long owner,UUID sid,String password,String secret){
        com.example.demo.auth.service.IdentityEntryBoundary.require();
        policy.validate(password);
        commands.execute(()->{
            users.getUserEntityForUpdate(owner);
            if(users.getUserSecuritySnapshot(owner).getEncodedPassword()!=null)throw new IdentityException(409,"PASSWORD_ALREADY_SET","A local password is already set");
            RecentAuthProof proof=recent.lockProof(owner,sid,RecentAuthProof.Operation.INITIAL_PASSWORD,secret);
            if(proof.getMethod()!=RecentAuthProof.Method.GOOGLE || identities.findById(proof.getProviderIdentityId()).filter(i->owner.equals(i.getUserId())).isEmpty())throw invalid();
            AuthenticationSession session=sessions.getSessionEntityForUpdate(sid);
            if(!owner.equals(session.getUserId()) || !session.isActiveAt(clock.instant()) || users.getUserSecuritySnapshot(owner).getAccountStatus()!=AccountStatus.ACTIVE)throw invalid();
            users.setInitialEncodedPassword(owner,passwords.encode(password));recent.consume(proof);sessions.revokeAll(owner,clock.instant());
            return IdentityCommandOutcome.accepted(null);
        });
    }
    private IdentityException invalid(){return new IdentityException(401,"AUTHENTICATION_FAILED","Authentication failed");}
}
