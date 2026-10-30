package com.example.demo.auth.dto.response;
import java.time.Instant;
import lombok.Getter;
import lombok.AllArgsConstructor;
@Getter @AllArgsConstructor
public class TokenResponse {
    private final String tokenType;
    private final long accessTokenExpiresIn;
    private final String refreshToken;
    private final Instant refreshTokenExpiresAt;
}
