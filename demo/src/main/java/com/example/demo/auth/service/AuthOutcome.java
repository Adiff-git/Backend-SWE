package com.example.demo.auth.service;
import lombok.Getter;
/** Rejection is returned through the transaction so security writes can commit. */
@Getter
public final class AuthOutcome {
    private final boolean accepted;
    private final IssuedTokens tokens;
    private AuthOutcome(boolean accepted,IssuedTokens tokens) {
        this.accepted=accepted;
        this.tokens=tokens;
    }
    public static AuthOutcome issued(IssuedTokens tokens) {
        return new AuthOutcome(true,tokens);
    }
    public static AuthOutcome success() {
        return new AuthOutcome(true,null);
    }
    public static AuthOutcome rejected() {
        return new AuthOutcome(false,null);
    }
}
