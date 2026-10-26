package com.example.demo.auth.security;
import com.example.demo.auth.config.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import static org.assertj.core.api.Assertions.*;
class AuthConfigurationTest {
    @Test void execute_shouldRejectMissingShortSecretIssuerAndWildcardCors() {
        assertThatThrownBy(()->new JwtProperties(null,"issuer","")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new JwtProperties("x".repeat(31),"issuer","")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new JwtProperties("x".repeat(32)," ","")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AuthSecurityConfiguration().corsConfigurationSource(List.of("*"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void execute_shouldVerifySignatureAndRejectsWrongKeyOrAlgorithm() {
        var config=new AuthSecurityConfiguration();
        var props=new JwtProperties("a".repeat(64),"urn:test","");
        Instant now=Instant.parse("2026-10-26T00:00:00Z");
        var claims=JwtClaimsSet.builder().issuer("urn:test").subject("1").claim("sid",UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plusSeconds(900)).build();
        var decoder=config.jwtDecoder(props,Clock.fixed(now,ZoneOffset.UTC));
        String valid=config.jwtEncoder(props).encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        assertThat(decoder.decode(valid).getSubject()).isEqualTo("1");
        String wrong=config.jwtEncoder(new JwtProperties("b".repeat(64),"urn:test","")).encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        assertThatThrownBy(()->decoder.decode(wrong)).isInstanceOf(JwtException.class);
        String hs512=config.jwtEncoder(props).encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS512).build(),claims)).getTokenValue();
        assertThatThrownBy(()->decoder.decode(hs512)).isInstanceOf(JwtException.class);
    }
}
