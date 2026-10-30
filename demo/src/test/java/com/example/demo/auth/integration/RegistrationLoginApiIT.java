package com.example.demo.auth.integration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
class RegistrationLoginApiIT extends AuthPostgresTestSupport {
    @Test void register_shouldIssueOnlyHeaderAccessAndAtomicDefaultAccount() throws Exception {
        org.springframework.test.web.servlet.MvcResult result=mvc.perform(post("/api/auth/register").header("Authorization","Bearer stale-invalid").contentType("application/json").content(json.writeValueAsString(Map.of("username"," Alice ","password","Password 123")))).andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().doesNotExist("Set-Cookie")).andExpect(jsonPath("$.accessToken").doesNotExist()).andExpect(jsonPath("$.tokenType").value("Bearer")).andReturn();
        assertThat(access(pair(result))).isEqualTo(200);
        assertThat(users.findByUsernameIdentifier("ALICE").orElseThrow().getUsername()).isEqualTo("Alice");
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(credentials.count()).isEqualTo(1);
        assertThat(users.findAuthorities(users.findByUsernameIdentifier("alice").orElseThrow().getId())).containsExactlyInAnyOrder("ROLE_USER","MANAGE_USER_PROFILE","MANAGE_ADDRESS");
    }
    @Test void login_shouldCreateIndependentSessionAndUseExactPassword() throws Exception {
        Pair first=register("Alice");
        org.springframework.test.web.servlet.MvcResult result=mvc.perform(post("/api/auth/login").header("Authorization","Bearer stale").contentType("application/json").content(json.writeValueAsString(Map.of("identifier"," ALICE ","password","Password 123")))).andExpect(status().isOk()).andReturn();
        assertThat(access(first)).isEqualTo(200);
        assertThat(access(pair(result))).isEqualTo(200);
        assertThat(sessions.count()).isEqualTo(2);
    }
    @Test void register_shouldRejectDuplicateAndAtUsernameWithoutPartialSession() throws Exception {
        register("Alice");
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("username","alice","password","Password 123")))).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("username","bad@name","password","Password 123")))).andExpect(status().isBadRequest());
        assertThat(users.count()).isEqualTo(1);
        assertThat(sessions.count()).isEqualTo(1);
    }
    @Test void login_shouldHideMissingWrongAndDisabledIdentities() throws Exception {
        register("Alice");
        for(String identifier:java.util.List.of("Alice","unknown","missing@example.com"))mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identifier",identifier,"password","Wrong password")))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication failed"));
        jdbc.update("UPDATE users SET account_status='INACTIVE' WHERE username='Alice'");
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identifier","Alice","password","Password 123")))).andExpect(status().isUnauthorized());
    }
    @Test void execute_shouldVerifyConcurrentDuplicateRegistrationCommitsOnlyOneAccount() throws Exception {
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> attempt=()-> {
                start.await();
                return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register").contentType("application/json").content("{\"username\":\"Alice\",\"password\":\"Password 123\"}")).andReturn().getResponse().getStatus();
            }
            ;
            var first=pool.submit(attempt);
            var second=pool.submit(attempt);
            start.countDown();
            assertThat(java.util.List.of(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(users.count()).isEqualTo(1);
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(credentials.count()).isEqualTo(1);
    }
}
