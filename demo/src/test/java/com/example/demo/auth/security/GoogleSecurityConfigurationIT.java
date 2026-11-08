package com.example.demo.auth.security;

import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.integration.AuthPostgresTestSupport;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class GoogleSecurityConfigurationIT extends AuthPostgresTestSupport {
    @Test void publicGoogleStarts_shouldAcceptRepeatedStartsWithoutThrottling() throws Exception {
        for(int index=0;index<25;index++)mvc.perform(post("/api/auth/google-login-attempts").header("Authorization","Bearer unrelated-invalid").contentType("application/json").content(startBody())).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oauth_login_attempts",Integer.class)).isEqualTo(25);
    }
    @Test void handoffFailures_shouldContinueValidatingRepeatedRequestsWithoutThrottling() throws Exception {
        mvc.perform(post("/api/auth/google-code-exchanges").contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        for(int index=0;index<30;index++)mvc.perform(post("/api/auth/google-code-exchanges").contentType("application/json").content(json.writeValueAsString(Map.of("code","x".repeat(43),"codeVerifier","v".repeat(43))))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/google-code-exchanges").contentType("application/json").content("{}")).andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Retry-After"));
        assertThat(users.count()).isZero();assertThat(sessions.count()).isZero();
    }
    @Test void exactChains_shouldAllowOnlyDeclaredPublicPostsAndExactCredentialedCors() throws Exception {
        mvc.perform(options("/api/auth/google-login-attempts").header("Origin","http://localhost:3000").header("Access-Control-Request-Method","POST")).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:3000")).andExpect(header().string("Access-Control-Allow-Credentials","true"));
        mvc.perform(options("/api/auth/google-login-attempts").header("Origin","https://untrusted.example").header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/registration-verifications/confirm").header("Authorization","Bearer unrelated-invalid").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/unapproved-route")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/google-login-attempts")).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
    }
    private String startBody(){return json.writeValueAsString(Map.of("codeChallenge",Base64.getUrlEncoder().withoutPadding().encodeToString(identityProofs.hash("v".repeat(43))),"codeChallengeMethod","S256"));}
}
