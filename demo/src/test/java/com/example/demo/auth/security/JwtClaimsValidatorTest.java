package com.example.demo.auth.security;
import com.example.demo.auth.validator.JwtClaimsValidator;
import com.example.demo.auth.config.JwtProperties;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.*;
class JwtClaimsValidatorTest {
    final Instant now=Instant.parse("2026-10-26T00:00:00Z");
    final JwtProperties properties=new JwtProperties("x".repeat(32),"urn:test","");
    Jwt token(Map<String,Object> replacements) {
        Map<String,Object> c=new HashMap<>();
        c.put("sub","1");
        c.put("sid",UUID.randomUUID().toString());
        c.put("iss","urn:test");
        c.put("iat",now.minusSeconds(1));
        c.put("exp",now.plusSeconds(899));
        c.putAll(replacements);
        return new Jwt("test",(Instant)c.get("iat"),(Instant)c.get("exp"),Map.of("alg","HS256"),c);
    }
    @Test void execute_shouldAcceptValidClaims() {
        assertThat(new JwtClaimsValidator(properties,Clock.fixed(now,ZoneOffset.UTC)).validate(token(Map.of())).hasErrors()).isFalse();
    }
    @Test void execute_shouldRejectExpiryEqualityFutureIssueAndExcessLifetime() {
        var validator=new JwtClaimsValidator(properties,Clock.fixed(now,ZoneOffset.UTC));
        for(Map<String,Object> overrides:List.<Map<String,Object>>of(Map.of("exp",now),Map.of("iat",now.plusSeconds(1)),Map.of("exp",now.plusSeconds(900)),Map.of("sub","01"),Map.of("sub",1),Map.of("sid","invalid"),Map.of("iss","other"),Map.of("userId","2"))) {
            assertThat(validator.validate(token(overrides)).hasErrors()).as(overrides.keySet().toString()).isTrue();
        }
    }
    @Test void execute_shouldEnforceAudienceOnlyWhenConfigured() {
        var validator=new JwtClaimsValidator(new JwtProperties("x".repeat(32),"urn:test","client"),Clock.fixed(now,ZoneOffset.UTC));
        assertThat(validator.validate(token(Map.of("aud",List.of("client")))).hasErrors()).isFalse();
        assertThat(validator.validate(token(Map.of("aud",List.of("other")))).hasErrors()).isTrue();
    }
}
