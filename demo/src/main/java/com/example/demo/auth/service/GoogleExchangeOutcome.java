package com.example.demo.auth.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Internal transport result; controller returns only the explicitly selected body DTO. */
@Getter @AllArgsConstructor
public class GoogleExchangeOutcome {
    private final IssuedTokens tokens;
    private final Object body;
    public static GoogleExchangeOutcome login(IssuedTokens tokens) { return new GoogleExchangeOutcome(tokens,tokens.getResponse()); }
}
