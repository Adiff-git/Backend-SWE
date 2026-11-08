package com.example.demo.auth.service.impl;

import com.example.demo.auth.entity.AuthenticationSession;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.auth.service.IssuedTokens;
import com.example.demo.auth.service.TokenService;
import com.example.demo.auth.service.VerifiedAccountSessionService;
import com.example.demo.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
@Transactional(propagation=Propagation.MANDATORY)
public class VerifiedAccountSessionServiceImpl implements VerifiedAccountSessionService {
    private final UserService users;
    private final AuthSessionService sessions;
    private final TokenService tokens;
    private final Clock clock;
    public IssuedTokens create(String email,String encodedPassword){
        Instant now=clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Long owner=users.createVerifiedAccount(email,encodedPassword,now);
        return issueForExisting(owner);
    }
    public IssuedTokens issueForExisting(Long owner){
        users.getUserEntityForUpdate(owner);
        if(users.getUserSecuritySnapshot(owner).getAccountStatus()!=com.example.demo.user.enums.AccountStatus.ACTIVE)throw new com.example.demo.auth.exception.IdentityException(403,"ACCOUNT_UNAVAILABLE","Account is unavailable");
        Instant now=clock.instant().truncatedTo(ChronoUnit.SECONDS);
        AuthenticationSession session=sessions.create(owner,now);
        String refresh=tokens.generateRefreshToken();sessions.createCredential(session,tokens.hash(refresh),now);
        return tokens.issue(session,refresh);
    }
}
