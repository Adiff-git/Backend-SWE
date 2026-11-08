package com.example.demo.auth.security;

import com.example.demo.config.ControlledOidcProvider;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ControlledOidcProviderTest {
    @Test void authorize_shouldProduceSignedCorrelatedOneUseIdentity() throws Exception {
        Instant now = Instant.parse("2026-11-05T00:00:00Z");
        try (ControlledOidcProvider provider = new ControlledOidcProvider(Clock.fixed(now, ZoneOffset.UTC))) {
            HttpClient client = HttpClient.newHttpClient();
            String callback = "http://127.0.0.1:8080/callback";
            HttpResponse<String> authorization = client.send(HttpRequest.newBuilder(URI.create(provider.issuer()
                    + "/authorize?client_id=controlled-client&response_type=code&state=state-one&nonce=nonce-one&redirect_uri="
                    + URLEncoder.encode(callback, StandardCharsets.UTF_8))).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(authorization.statusCode()).isEqualTo(302);
            String location = authorization.headers().firstValue("Location").orElseThrow();
            assertThat(location).contains("state=state-one");
            String code = URI.create(location).getQuery().split("&")[0].substring("code=".length());
            String body = "grant_type=authorization_code&code=" + code + "&redirect_uri="
                    + URLEncoder.encode(callback, StandardCharsets.UTF_8);
            HttpRequest exchange = HttpRequest.newBuilder(URI.create(provider.issuer() + "/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(exchange, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            SignedJWT jwt = SignedJWT.parse((String) JSONObjectUtils.parse(response.body()).get("id_token"));
            assertThat(jwt.verify(new RSASSAVerifier(provider.publicKey()))).isTrue();
            assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(provider.issuer());
            assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly(ControlledOidcProvider.CLIENT_ID);
            assertThat(jwt.getJWTClaimsSet().getStringClaim("nonce")).isEqualTo("nonce-one");
            assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(now.plusSeconds(300));
            assertThat(client.send(exchange, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
        }
    }
}
