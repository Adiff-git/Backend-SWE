package com.example.demo.auth.service;
import com.example.demo.auth.dto.response.TokenResponse;
import lombok.Getter;
import lombok.AllArgsConstructor;
/** Sensitive transient result: deliberately no toString and never an HTTP body. */
@Getter @AllArgsConstructor
public final class IssuedTokens {
    private final String accessToken;
    private final TokenResponse response;
}
