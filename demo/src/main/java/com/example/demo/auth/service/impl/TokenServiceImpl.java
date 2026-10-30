package com.example.demo.auth.service.impl;
import com.example.demo.auth.service.TokenService;
import com.example.demo.auth.service.IssuedTokens;
import com.example.demo.auth.entity.AuthenticationSession;
import com.example.demo.auth.dto.response.TokenResponse;
import com.example.demo.auth.config.JwtProperties;
import com.example.demo.auth.exception.AuthenticationFailedException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    public String generateRefreshToken() {
        byte[] bytes=new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        }
        catch(NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required digest is unavailable");
        }
    }
    public IssuedTokens issue(AuthenticationSession session,String refresh) {
        Instant now=clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant deadline=now.plusSeconds(900);
        if(session.getExpiresAt().isBefore(deadline))deadline=session.getExpiresAt();
        if(!clock.instant().isBefore(deadline))throw new AuthenticationFailedException();
        JwtClaimsSet.Builder claims=JwtClaimsSet.builder().subject(session.getUserId().toString()).claim("sid",session.getId().toString()).issuer(properties.issuer()).issuedAt(now).expiresAt(deadline);
        if(!properties.audience().isBlank())claims.audience(java.util.List.of(properties.audience()));
        String access=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims.build())).getTokenValue();
        return new IssuedTokens(access,new TokenResponse("Bearer",ChronoUnit.SECONDS.between(now,deadline),refresh,session.getExpiresAt()));
    }
}
