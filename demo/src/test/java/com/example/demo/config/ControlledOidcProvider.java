package com.example.demo.config;

import com.example.demo.testdata.auth.GoogleIdentityTestData;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Local controlled provider. No production trust override or live Google traffic. */
public final class ControlledOidcProvider implements AutoCloseable {
    public static final String CLIENT_ID = "controlled-client";
    private final HttpServer server;
    private final Clock clock;
    private final RSAKey key;
    private final RSAKey incorrectSigningKey;
    public enum TokenFault { NONE,NONCE,ISSUER,AUDIENCE,EXPIRED,SIGNATURE,OLD_AUTH_TIME,MISSING_AUTH_TIME,TOKEN_OUTAGE }
    private volatile TokenFault fault=TokenFault.NONE;
    private volatile boolean denied;
    public void denyConsent(boolean value){denied=value;}
    public void fault(TokenFault value){fault=value;}
    private final Map<String, Map<String, String>> grants = new ConcurrentHashMap<>();
    private volatile Map<String, Object> identity = GoogleIdentityTestData.verified();

    public ControlledOidcProvider(Clock clock) throws IOException, JOSEException {
        this.clock = clock;
        key = new RSAKeyGenerator(2048).keyID("controlled-key").generate();
        incorrectSigningKey=new RSAKeyGenerator(2048).keyID("incorrect-signing-key").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/openid-configuration", exchange -> json(exchange, 200, Map.of(
                "issuer", issuer(), "authorization_endpoint", issuer() + "/authorize",
                "token_endpoint", issuer() + "/token", "jwks_uri", issuer() + "/jwks",
                "response_types_supported", java.util.List.of("code"),
                "subject_types_supported", java.util.List.of("public"),
                "id_token_signing_alg_values_supported", java.util.List.of("RS256"))));
        server.createContext("/jwks", exchange -> json(exchange, 200,
                Map.of("keys", java.util.List.of(key.toPublicJWK().toJSONObject()))));
        server.createContext("/authorize", this::authorize);
        server.createContext("/token", this::token);
        server.createContext("/userinfo", exchange -> json(exchange,200,identity));
        server.start();
    }

    public String issuer() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    public RSAKey publicKey() { return key.toPublicJWK(); }
    public void identity(Map<String, Object> claims) { identity = Map.copyOf(claims); }

    private void authorize(HttpExchange exchange) throws IOException {
        Map<String, String> request = form(exchange.getRequestURI().getRawQuery());
        if (!CLIENT_ID.equals(request.get("client_id")) || !request.containsKey("redirect_uri")
                || !request.containsKey("state") || !"code".equals(request.get("response_type"))) {
            json(exchange, 400, Map.of("error", "invalid_request"));
            return;
        }
        if(denied){
            exchange.getResponseHeaders().set("Location",request.get("redirect_uri")+"?error=access_denied&state="+URLEncoder.encode(request.get("state"),StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(302,-1);exchange.close();return;
        }
        String code = UUID.randomUUID().toString();
        grants.put(code, Map.copyOf(request));
        String target = request.get("redirect_uri") + "?code=" + code + "&state="
                + URLEncoder.encode(request.get("state"), StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Location", target);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> request = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Map<String, String> grant = grants.remove(request.getOrDefault("code", ""));
        if (grant == null || !"authorization_code".equals(request.get("grant_type"))
                || !grant.get("redirect_uri").equals(request.get("redirect_uri")) || !validPkce(grant, request)) {
            json(exchange, 400, Map.of("error", "invalid_grant"));
            return;
        }
        if(fault==TokenFault.TOKEN_OUTAGE){json(exchange,503,Map.of("error","server_error"));return;}
        Instant now = clock.instant();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer(issuer()).audience(CLIENT_ID)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
                .claim("auth_time", now.getEpochSecond());
        identity.forEach(claims::claim);
        if (grant.containsKey("nonce")) claims.claim("nonce", grant.get("nonce"));
        switch(fault){
            case NONCE -> claims.claim("nonce","unrelated-nonce");
            case ISSUER -> claims.issuer("https://incorrect.example");
            case AUDIENCE -> claims.audience("different-client");
            case EXPIRED -> claims.issueTime(Date.from(now.minusSeconds(600))).expirationTime(Date.from(now.minusSeconds(300)));
            case OLD_AUTH_TIME -> claims.claim("auth_time",now.minusSeconds(360).getEpochSecond());
            case MISSING_AUTH_TIME -> claims.claim("auth_time",null);
            default -> { }
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
        try {
            jwt.sign(new RSASSASigner(fault==TokenFault.SIGNATURE?incorrectSigningKey:key));
            json(exchange, 200, Map.of("access_token", "controlled-provider-token", "token_type", "Bearer",
                    "expires_in", 300, "id_token", jwt.serialize()));
        } catch (JOSEException failure) {
            json(exchange, 500, Map.of("error", "server_error"));
        }
    }

    private boolean validPkce(Map<String, String> grant, Map<String, String> request) {
        if (!grant.containsKey("code_challenge")) return true;
        if (!"S256".equals(grant.get("code_challenge_method")) || !request.containsKey("code_verifier")) return false;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(request.get("code_verifier").getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).equals(grant.get("code_challenge"));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Map<String, String> form(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value == null || value.isEmpty()) return result;
        for (String part : value.split("&")) {
            String[] pair = part.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8));
        }
        return result;
    }
    private static void json(HttpExchange exchange, int status, Map<String, Object> value) throws IOException {
        byte[] bytes = JSONObjectUtils.toJSONString(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }
    @Override public void close() { server.stop(0); }
}



