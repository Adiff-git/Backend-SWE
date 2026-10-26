package com.example.demo.auth.service.impl;
import com.example.demo.auth.service.AuthService;
import com.example.demo.auth.service.AuthTransactionService;
import com.example.demo.auth.service.AuthOutcome;
import com.example.demo.auth.service.IssuedTokens;
import com.example.demo.auth.exception.AuthenticationFailedException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.RequiredArgsConstructor;
/** Deliberately nontransactional: security rejection is raised only after command commit. */
@Service @RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AuthTransactionService commands;
    public IssuedTokens register(String username,String password) {
        requireEntryBoundary();
        return accepted(commands.register(username,password)).getTokens();
    }
    public IssuedTokens login(String identifier,String password) {
        requireEntryBoundary();
        return accepted(commands.login(identifier,password)).getTokens();
    }
    public IssuedTokens refresh(String refreshToken) {
        requireEntryBoundary();
        return accepted(commands.refresh(refreshToken)).getTokens();
    }
    public void logout(String refreshToken) {
        requireEntryBoundary();
        accepted(commands.logout(refreshToken));
    }
    public void changePassword(Long owner,UUID sid,String current,String replacement) {
        requireEntryBoundary();
        accepted(commands.changePassword(owner,sid,current,replacement));
    }
    private AuthOutcome accepted(AuthOutcome outcome) {
        if(!outcome.isAccepted())throw new AuthenticationFailedException();
        return outcome;
    }
    private void requireEntryBoundary() {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Authentication facade must not join an ambient transaction");
    }
}
