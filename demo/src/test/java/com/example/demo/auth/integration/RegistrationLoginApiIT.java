package com.example.demo.auth.integration;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class RegistrationLoginApiIT extends AuthPostgresTestSupport {
    @Test void confirmation_shouldIssueOnlyHeaderAccessAndAtomicDefaultAccount() throws Exception {
        MvcResult initial=mvc.perform(post("/api/auth/register").header("Authorization","Bearer stale-invalid").contentType("application/json").content(json.writeValueAsString(Map.of("email"," Alice@example.com ","password","Initial 123"))))
            .andExpect(status().isAccepted()).andExpect(header().doesNotExist("Authorization")).andExpect(header().doesNotExist("Set-Cookie")).andExpect(jsonPath("refreshToken").doesNotExist()).andReturn();
        assertThat(users.count()).isZero();assertThat(sessions.count()).isZero();
        MvcResult result=confirmRegistration(initial,"Password 123");
        assertThat(access(pair(result))).isEqualTo(200);assertThat(users.findByUsernameIdentifier("ALICE").orElseThrow().getUsername()).isEqualTo("alice");
        assertThat(sessions.count()).isEqualTo(1);assertThat(credentials.count()).isEqualTo(1);
        assertThat(users.findAuthorities(users.findByUsernameIdentifier("alice").orElseThrow().getId())).containsExactlyInAnyOrder("ROLE_USER","MANAGE_USER_PROFILE","MANAGE_ADDRESS","MANAGE_WISHLIST");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }
    @Test void login_shouldCreateIndependentSessionAndUseExactPassword() throws Exception {
        Pair first=register("Alice");
        MvcResult result=mvc.perform(post("/api/auth/login").header("Authorization","Bearer stale").contentType("application/json").content(json.writeValueAsString(Map.of("identifier"," ALICE ","password","Password 123")))).andExpect(status().isOk()).andReturn();
        assertThat(access(first)).isEqualTo(200);assertThat(access(pair(result))).isEqualTo(200);assertThat(sessions.count()).isEqualTo(2);
    }
    @Test void register_shouldRejectLegacyShapeAndInvalidEmailWithoutPartialSession() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("username","alice","password","Password 123")))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("email","bad@name","password","Password 123")))).andExpect(status().isBadRequest());
        assertThat(users.count()).isZero();assertThat(sessions.count()).isZero();
    }
    @Test void login_shouldHideMissingWrongAndDisabledIdentities() throws Exception {
        register("Alice");
        for(String identifier:java.util.List.of("Alice","unknown","missing@example.com"))mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identifier",identifier,"password","Wrong password")))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication failed"));
        jdbc.update("UPDATE users SET account_status='INACTIVE' WHERE username='alice'");
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identifier","Alice","password","Password 123")))).andExpect(status().isUnauthorized());
    }
    @Test void confirmation_shouldAllowOnlyOneConcurrentCredentialIssue() throws Exception {
        MvcResult initial=mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"email\":\"race@example.com\",\"password\":\"Initial 123\"}")).andExpect(status().isAccepted()).andReturn();
        tools.jackson.databind.JsonNode pending=json.readTree(initial.getResponse().getContentAsString());String challenge=pending.get("challengeId").asString();
        byte[] payload=jdbc.queryForObject("SELECT encrypted_payload FROM verification_mail_outbox WHERE challenge_id=?",byte[].class,java.util.UUID.fromString(challenge));
        String code=(String)json.readValue(identityProofs.decrypt(payload,challenge),Map.class).get("code");
        String body=json.writeValueAsString(Map.of("registrationCredential",pending.get("registrationCredential").asString(),"challengeId",challenge,"code",code,"password","Password 123"));
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        try(java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            java.util.concurrent.Callable<Integer> operation=()->{start.await();return mvc.perform(post("/api/auth/registration-verifications/confirm").contentType("application/json").content(body)).andReturn().getResponse().getStatus();};
            java.util.concurrent.Future<Integer> first=pool.submit(operation),second=pool.submit(operation);start.countDown();
            assertThat(java.util.List.of(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,400);
        }
        assertThat(users.count()).isOne();assertThat(sessions.count()).isOne();assertThat(credentials.count()).isOne();
    }
}
