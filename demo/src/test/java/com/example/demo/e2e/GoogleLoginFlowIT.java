package com.example.demo.e2e;

import com.example.demo.auth.integration.AuthPostgresTestSupport;
import com.example.demo.config.ControlledOidcProvider;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

class GoogleLoginFlowIT extends AuthPostgresTestSupport {
    private static final ControlledOidcProvider PROVIDER=provider();
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry registry){registry.add("security.identity.test-issuer",PROVIDER::issuer);}
    @AfterAll static void close(){PROVIDER.close();}
    @Test void newAndReturningGoogleIdentity_shouldUseSameSocialOnlyAccountWithoutOverwritingOfficialEmail() throws Exception {
        PROVIDER.identity(Map.of("sub","first-subject","email","social@example.com","email_verified",true,"name","Social Name"));
        Pair first=pair(exchange(handoff(),200));assertThat(access(first)).isEqualTo(200);
        assertThat(users.count()).isOne();assertThat(users.findAll().getFirst().getPassword()).isNull();
        assertThat(users.findAll().getFirst().getEmailVerifiedAt()).isNotNull();assertThat(users.findAll().getFirst().getDisplayName()).isEqualTo("Social Name");
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"identifier\":\"social@example.com\",\"password\":\"Password 123\"}")).andExpect(status().isUnauthorized());
        PROVIDER.identity(Map.of("sub","first-subject"));Pair returning=pair(exchange(handoff(),200));
        assertThat(access(returning)).isEqualTo(200);assertThat(users.count()).isOne();assertThat(sessions.count()).isEqualTo(2);
        assertThat(users.findAll().getFirst().getEmail()).isEqualTo("social@example.com");
    }
    @Test void matchingEmail_shouldRequireExplicitConnectionWithoutCreatingAssociationOrSession() throws Exception {
        register("Owner");PROVIDER.identity(Map.of("sub","unlinked-subject","email","owner@example.com","email_verified",true));
        exchange(handoff(),409);assertThat(users.count()).isOne();assertThat(sessions.count()).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM google_identities",Integer.class)).isZero();
    }
    @Test void missingVerifiedEmail_shouldRejectOnlyNewIdentityWithoutOrphanRows() throws Exception {
        PROVIDER.identity(Map.of("sub","unverified-subject","email","social@example.com","email_verified",false));
        exchange(handoff(),400);assertThat(users.count()).isZero();assertThat(sessions.count()).isZero();assertThat(credentials.count()).isZero();
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"","not-an-email"})
    void newIdentity_shouldRejectMissingOrMalformedVerifiedEmail(String email) throws Exception {
        PROVIDER.identity(Map.of("sub","malformed-email-subject","email",email,"email_verified",true));exchange(handoff(),400);assertThat(users.count()).isZero();assertThat(sessions.count()).isZero();
    }
    @Test void returningIdentity_shouldRecheckCurrentEligibilityAtExchange() throws Exception {
        PROVIDER.identity(Map.of("sub","ineligible-subject","email","ineligible@example.com","email_verified",true));exchange(handoff(),200);
        jdbc.update("UPDATE users SET account_status='INACTIVE'");exchange(handoff(),403);assertThat(sessions.count()).isOne();assertThat(users.count()).isOne();
    }
    private String handoff() throws Exception {
        String verifier="v".repeat(43);String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(identityProofs.hash(verifier));
        MvcResult start=mvc.perform(post("/api/auth/google-login-attempts").contentType("application/json").content(json.writeValueAsString(Map.of("codeChallenge",challenge,"codeChallengeMethod","S256")))).andExpect(status().isCreated()).andReturn();
        String setCookie=start.getResponse().getHeader("Set-Cookie");Cookie cookie=new Cookie("google_handshake",setCookie.substring(setCookie.indexOf('=')+1,setCookie.indexOf(';')));
        JsonNode body=json.readTree(start.getResponse().getContentAsString());String provider=mvc.perform(get(URI.create(body.get("authorizationUrl").asString())).cookie(cookie)).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        String callback=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(provider)).GET().build(),HttpResponse.BodyHandlers.discarding()).headers().firstValue("Location").orElseThrow();
        String destination=mvc.perform(get(URI.create(callback)).cookie(cookie)).andExpect(status().isFound()).andExpect(header().doesNotExist("Set-Cookie")).andExpect(header().doesNotExist("Authorization")).andReturn().getResponse().getHeader("Location");
        assertThat(destination).contains("/auth/success?code=");return URLDecoder.decode(destination.substring(destination.indexOf("?code=")+6),StandardCharsets.UTF_8);
    }
    private MvcResult exchange(String code,int expected) throws Exception {
        return mvc.perform(post("/api/auth/google-code-exchanges").header("Authorization","Bearer ignored-invalid").contentType("application/json").content(json.writeValueAsString(Map.of("code",code,"codeVerifier","v".repeat(43))))).andExpect(status().is(expected)).andExpect(header().string("Cache-Control","no-store")).andReturn();
    }
    private static ControlledOidcProvider provider(){try{return new ControlledOidcProvider(Clock.systemUTC());}catch(Exception failure){throw new ExceptionInInitializerError(failure);}}
}
