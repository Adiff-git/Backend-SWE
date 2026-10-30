package com.example.demo.auth.service.impl;
import com.example.demo.auth.service.AuthTransactionService;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.auth.service.TokenService;
import com.example.demo.auth.service.AuthOutcome;
import com.example.demo.auth.entity.AuthenticationSession;
import com.example.demo.auth.entity.RefreshCredential;
import com.example.demo.auth.validator.PasswordPolicyValidator;
import com.example.demo.auth.security.CustomUserDetails;
import com.example.demo.auth.exception.AuthenticationFailedException;
import com.example.demo.user.service.UserService;
import com.example.demo.user.dto.response.UserSecuritySnapshot;
import com.example.demo.user.enums.AccountStatus;
import com.example.demo.user.exception.BusinessValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class AuthTransactionServiceImpl implements AuthTransactionService {
    private final UserService users;
    private final AuthSessionService sessions;
    private final TokenService tokens;
    private final PasswordPolicyValidator policy;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authentication;
    private final Clock clock;
    public AuthOutcome register(String username,String password) {
        policy.validate(password);
        Long id=users.createAuthenticationUser(username,encoder.encode(password));
        users.getUserEntityForUpdate(id);
        Authentication result=authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username,password));
        if(!(result.getPrincipal() instanceof CustomUserDetails details) || !id.equals(details.getId()))throw new AuthenticationFailedException();
        return issueNew(id);
    }
    public AuthOutcome login(String identifier,String password) {
        if(password==null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new AuthenticationFailedException();
        Optional<Long> candidate=users.resolveSecurityIdentifier(identifier);
        if(candidate.isEmpty()) {
            try {
                authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(identifier,password));
            }
            catch(AuthenticationException expected) {
                throw new AuthenticationFailedException();
            }
            throw new AuthenticationFailedException();
        }
        Long id=candidate.get();
        users.getUserEntityForUpdate(id);
        if(!users.resolveSecurityIdentifier(identifier).filter(id::equals).isPresent())throw new AuthenticationFailedException();
        try {
            Authentication result=authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(identifier,password));
            if(!(result.getPrincipal() instanceof CustomUserDetails details) || !id.equals(details.getId()))throw new AuthenticationFailedException();
        }
        catch(AuthenticationException failure) {
            throw new AuthenticationFailedException();
        }
        return issueNew(id);
    }
    private AuthOutcome issueNew(Long id) {
        Instant now=clock.instant().truncatedTo(ChronoUnit.SECONDS);
        AuthenticationSession session=sessions.create(id,now);
        String refresh=tokens.generateRefreshToken();
        sessions.createCredential(session,tokens.hash(refresh),now);
        return AuthOutcome.issued(tokens.issue(session,refresh));
    }
    public AuthOutcome refresh(String raw) {
        // Lookup is only a routing hint. Eligibility is checked after owner and child locks.
        Optional<RefreshCredential> hint=sessions.findCredentialEntity(tokens.hash(raw));
        if(hint.isEmpty())return AuthOutcome.rejected();
        Optional<AuthenticationSession> candidate=sessions.findSessionEntity(hint.get().getSessionId());
        if(candidate.isEmpty())return AuthOutcome.rejected();
        Long owner=candidate.get().getUserId();
        users.getUserEntityForUpdate(owner);
        AuthenticationSession session=sessions.getSessionEntityForUpdate(candidate.get().getId());
        RefreshCredential credential=sessions.getCredentialEntityForUpdate(hint.get().getId());
        Instant now=clock.instant();
        if(!owner.equals(session.getUserId()) || !credential.getSessionId().equals(session.getId()) || !session.isActiveAt(now))return AuthOutcome.rejected();
        if(credential.getConsumedAt()!=null) {
            // Return normally so revocation commits before the facade raises its 401.
            sessions.revoke(session,now,"REFRESH_REUSE");
            return AuthOutcome.rejected();
        }
        if(users.getUserSecuritySnapshot(owner).getAccountStatus()!=AccountStatus.ACTIVE)return AuthOutcome.rejected();
        // Release the partial unique index before inserting the new current credential.
        sessions.consume(credential,now);
        String refresh=tokens.generateRefreshToken();
        sessions.createCredential(session,tokens.hash(refresh),now);
        return AuthOutcome.issued(tokens.issue(session,refresh));
    }
    public AuthOutcome logout(String raw) {
        // Lookup is only a routing hint. Eligibility is checked after owner and child locks.
        Optional<RefreshCredential> hint=sessions.findCredentialEntity(tokens.hash(raw));
        if(hint.isEmpty())return AuthOutcome.rejected();
        Optional<AuthenticationSession> candidate=sessions.findSessionEntity(hint.get().getSessionId());
        if(candidate.isEmpty())return AuthOutcome.rejected();
        users.getUserEntityForUpdate(candidate.get().getUserId());
        AuthenticationSession session=sessions.getSessionEntityForUpdate(candidate.get().getId());
        RefreshCredential credential=sessions.getCredentialEntityForUpdate(hint.get().getId());
        Instant now=clock.instant();
        if(!credential.getSessionId().equals(session.getId()) || !now.isBefore(session.getExpiresAt()))return AuthOutcome.rejected();
        sessions.revoke(session,now,"LOGOUT");
        return AuthOutcome.success();
    }
    public AuthOutcome changePassword(Long owner,UUID sid,String current,String replacement) {
        users.getUserEntityForUpdate(owner);
        // The owner lock serializes mutations; revokeAll locks sessions in UUID order.
        AuthenticationSession session=sessions.findSessionEntity(sid).orElseThrow(AuthenticationFailedException::new);
        Instant now=clock.instant();
        UserSecuritySnapshot user=users.getUserSecuritySnapshot(owner);
        if(!owner.equals(session.getUserId()) || !session.isActiveAt(now) || user.getAccountStatus()!=AccountStatus.ACTIVE)throw new AuthenticationFailedException();
        if(current==null || current.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || !encoder.matches(current,user.getEncodedPassword()))throw new BusinessValidationException("Current password is incorrect");
        policy.validate(replacement);
        if(encoder.matches(replacement,user.getEncodedPassword()))throw new BusinessValidationException("New password must be different");
        users.replaceEncodedPassword(owner,encoder.encode(replacement));
        sessions.revokeAll(owner,now);
        return AuthOutcome.success();
    }
    public void cleanupOwner(Long owner,Instant deadline) {
        users.getUserEntityForUpdate(owner);
        sessions.purgeExpired(owner,deadline);
    }
}
