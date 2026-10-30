package com.example.demo.auth.validator;
import com.example.demo.auth.config.JwtProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
public final class JwtClaimsValidator implements OAuth2TokenValidator<Jwt> {
    private final JwtProperties properties;
    private final Clock clock;
    public JwtClaimsValidator(JwtProperties properties,Clock clock) {
        this.properties=properties;
        this.clock=clock;
    }
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            if(!(jwt.getClaims().get("sub") instanceof String) || !(jwt.getClaims().get("sid") instanceof String) || !(jwt.getClaims().get("iss") instanceof String))return invalid();
            String subject=jwt.getSubject();
            Long id=Long.valueOf(subject);
            String sid=jwt.getClaimAsString("sid");
            Instant now=clock.instant();
            Instant issued=jwt.getIssuedAt();
            Instant expires=jwt.getExpiresAt();
            if(id<=0 || !id.toString().equals(subject) || !UUID.fromString(sid).toString().equals(sid) || jwt.getClaimAsString("iss")==null || !properties.issuer().equals(jwt.getClaimAsString("iss")) || issued==null || issued.isAfter(now) || expires==null || !now.isBefore(expires) || !issued.isBefore(expires) || expires.isAfter(issued.plusSeconds(900)))return invalid();
            if(jwt.hasClaim("userId") && !subject.equals(String.valueOf(jwt.getClaim("userId"))))return invalid();
            if(!properties.audience().isBlank() && !jwt.getAudience().contains(properties.audience()))return invalid();
            return OAuth2TokenValidatorResult.success();
        }
        catch(RuntimeException failure) {
            return invalid();
        }
    }
    private OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Authentication failed",null));
    }
}
