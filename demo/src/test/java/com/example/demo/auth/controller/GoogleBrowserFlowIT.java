package com.example.demo.auth.controller;

import com.example.demo.auth.security.GoogleCookieSupport;
import com.example.demo.auth.service.IdentityProofService;
import com.example.demo.config.ControlledOidcProvider;
import com.example.demo.user.integration.PostgresTestSupport;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles({"test","identity-test"})
class GoogleBrowserFlowIT extends PostgresTestSupport {
    private static final ControlledOidcProvider PROVIDER=provider();
    @DynamicPropertySource static void providerProperties(DynamicPropertyRegistry registry){registry.add("security.identity.test-issuer",PROVIDER::issuer);}
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired IdentityProofService proofs;
    @Autowired JdbcTemplate jdbc;
    @AfterAll static void close(){PROVIDER.close();}
    @AfterEach void cleanup(){
        PROVIDER.fault(ControlledOidcProvider.TokenFault.NONE);PROVIDER.denyConsent(false);
        jdbc.update("DELETE FROM oauth_handoffs");jdbc.update("UPDATE oauth_browser_slots SET current_attempt_id=NULL");
        jdbc.update("DELETE FROM oauth_login_attempts");jdbc.update("DELETE FROM oauth_browser_slots");


    }
    @Test void callback_shouldValidateRealOidcAndNeverWriteCookiesOrIssueApplicationCredentials() throws Exception {
        MvcResult initiation=start(null);Cookie cookie=cookie(initiation);
        assertThat(initiation.getResponse().getHeader("Set-Cookie")).contains("Secure","HttpOnly","SameSite=Lax","Path=/","Max-Age=300");
        assertThat(initiation.getResponse().getHeader("Authorization")).isNull();
        String callback=callback(initiation,cookie);
        MvcResult completed=mvc.perform(get(URI.create(callback)).cookie(cookie)).andExpect(status().isFound()).andReturn();
        assertThat(completed.getResponse().getHeader("Location")).startsWith("http://localhost:3000/auth/success?code=");
        assertThat(completed.getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(completed.getResponse().getHeader("Authorization")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM authentication_sessions",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class)).isZero();
        MvcResult replay=mvc.perform(get(URI.create(callback)).cookie(cookie)).andExpect(status().isFound()).andReturn();
        assertThat(replay.getResponse().getHeader("Location")).startsWith("http://localhost:3000/auth/failure?");
        assertThat(replay.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }
    @Test void olderCallback_shouldNotEraseNewerInitiationCookie() throws Exception {
        MvcResult old=start(null);Cookie oldCookie=cookie(old);String callback=callback(old,oldCookie);
        MvcResult latest=start(oldCookie);Cookie latestCookie=cookie(latest);
        MvcResult stale=mvc.perform(get(URI.create(callback)).cookie(oldCookie)).andExpect(status().isFound()).andReturn();
        assertThat(stale.getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(stale.getResponse().getHeader("Location")).contains("/auth/failure?");
        assertThat(mvc.perform(get(URI.create(authorization(latest))).cookie(latestCookie)).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location")).startsWith(PROVIDER.issuer()+"/authorize");
    }
    @Test void publicStart_shouldIgnoreUnrelatedBearerAndRejectUntrustedOrigin() throws Exception {
        mvc.perform(post("/api/auth/google-login-attempts").header("Origin","https://evil.example").contentType("application/json").content(body())).andExpect(status().isForbidden());
        assertThat(start(null).getResponse().getStatus()).isEqualTo(201);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=ControlledOidcProvider.TokenFault.class,names={"NONCE","ISSUER","AUDIENCE","EXPIRED","SIGNATURE","TOKEN_OUTAGE"})
    void callback_shouldRejectInvalidProviderProofWithoutCookiesOrCredentials(ControlledOidcProvider.TokenFault fault) throws Exception {
        PROVIDER.fault(fault);MvcResult initiation=start(null);Cookie cookie=cookie(initiation);String callback=callback(initiation,cookie);
        MvcResult failed=mvc.perform(get(URI.create(callback)).cookie(cookie)).andExpect(status().isFound()).andReturn();
        assertThat(failed.getResponse().getHeader("Location")).startsWith("http://localhost:3000/auth/failure?error=");
        assertThat(failed.getResponse().getHeaders("Set-Cookie")).isEmpty();assertThat(failed.getResponse().getHeader("Authorization")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oauth_handoffs",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM authentication_sessions",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class)).isZero();
    }
    @Test void cancelledConsent_shouldConsumeServerAttemptWithoutTouchingCookie() throws Exception {
        PROVIDER.denyConsent(true);MvcResult initiation=start(null);Cookie cookie=cookie(initiation);String callback=callback(initiation,cookie);
        MvcResult failed=mvc.perform(get(URI.create(callback)).cookie(cookie)).andExpect(status().isFound()).andReturn();
        assertThat(failed.getResponse().getHeader("Location")).isEqualTo("http://localhost:3000/auth/failure?error=cancelled");assertThat(failed.getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(mvc.perform(get(URI.create(callback)).cookie(cookie)).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location")).contains("/auth/failure?");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oauth_handoffs",Integer.class)).isZero();
    }
    @Test void missingCookieAndTamperedState_shouldFailSafelyAndNeverRepairBrowserCorrelation() throws Exception {
        MvcResult initiation=start(null);Cookie cookie=cookie(initiation);String callback=callback(initiation,cookie);
        MvcResult missing=mvc.perform(get(URI.create(callback))).andExpect(status().isFound()).andReturn();assertThat(missing.getResponse().getHeaders("Set-Cookie")).isEmpty();assertThat(missing.getResponse().getHeader("Location")).contains("/auth/failure?");
        assertThat(mvc.perform(get(URI.create(callback)).cookie(cookie)).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location")).contains("/auth/failure?");
        MvcResult tampered=mvc.perform(get("/login/oauth2/code/google").param("state","unknown-state").param("code","unknown-code").cookie(cookie)).andExpect(status().isFound()).andReturn();
        assertThat(tampered.getResponse().getHeader("Location")).contains("/auth/failure?");assertThat(tampered.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }
    @Test void delayedSuccessfulCallbackResponse_shouldNotOverwriteCookieFromLaterInitiation() throws Exception {
        MvcResult initial=start(null);Cookie original=cookie(initial);String callback=callback(initial,original);
        MvcResult delayedResponse=mvc.perform(get(URI.create(callback)).cookie(original)).andExpect(status().isFound()).andReturn();
        assertThat(delayedResponse.getResponse().getHeader("Location")).contains("/auth/success?code=");
        MvcResult newer=start(original);Cookie current=cookie(newer);
        // Delivering the already-built callback response after newer initiation has no cookie effect.
        assertThat(delayedResponse.getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(mvc.perform(get(URI.create(authorization(newer))).cookie(current)).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location")).startsWith(PROVIDER.issuer()+"/authorize");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM authentication_sessions",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class)).isZero();
    }
    private MvcResult start(Cookie cookie) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request=post("/api/auth/google-login-attempts").header("Origin","http://localhost:3000").header("Authorization","Bearer deliberately-invalid").contentType("application/json").content(body());
        if(cookie!=null)request.cookie(cookie);
        return mvc.perform(request).andExpect(status().isCreated()).andReturn();
    }
    private String body(){return json.writeValueAsString(Map.of("codeChallenge",Base64.getUrlEncoder().withoutPadding().encodeToString(proofs.hash("v".repeat(43))),"codeChallengeMethod","S256"));}
    private String authorization(MvcResult start) throws Exception {return json.readTree(start.getResponse().getContentAsString()).get("authorizationUrl").asString();}
    private Cookie cookie(MvcResult result){String header=result.getResponse().getHeader("Set-Cookie");return new Cookie(GoogleCookieSupport.NAME,header.substring(header.indexOf('=')+1,header.indexOf(';')));}
    private String callback(MvcResult start,Cookie cookie) throws Exception {
        String location=mvc.perform(get(URI.create(authorization(start))).cookie(cookie)).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(location)).GET().build(),HttpResponse.BodyHandlers.discarding()).headers().firstValue("Location").orElseThrow();
    }
    private static ControlledOidcProvider provider(){try{return new ControlledOidcProvider(Clock.systemUTC());}catch(Exception failure){throw new ExceptionInInitializerError(failure);}}
}
