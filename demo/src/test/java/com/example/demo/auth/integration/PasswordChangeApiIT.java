package com.example.demo.auth.integration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
class PasswordChangeApiIT extends AuthPostgresTestSupport {
    @Test void changePassword_shouldRevokeEverySessionAndNotIssueCredentials() throws Exception {
        Pair first=register("Alice");
        Pair other=pair(mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identifier","Alice","password","Password 123")))).andExpect(status().isOk()).andReturn());
        mvc.perform(put("/api/auth/password").header("Authorization",first.access).contentType("application/json").content(json.writeValueAsString(Map.of("currentPassword","Password 123","newPassword","Replacement 123")))).andExpect(status().isNoContent()).andExpect(header().doesNotExist("Authorization"));
        assertThat(access(first)).isEqualTo(401);
        assertThat(access(other)).isEqualTo(401);
        refresh(first.refresh).andExpect(status().isUnauthorized());
        assertThat(sessions.findAll()).allMatch(s->s.getRevokedAt()!=null);
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identifier","Alice","password","Replacement 123")))).andExpect(status().isOk());
    }
    @Test void changePassword_shouldPreserveStateForWrongOrUnchangedPassword() throws Exception {
        Pair first=register("Alice");
        for(Map<String,String> body:java.util.List.of(Map.of("currentPassword","Wrong password","newPassword","Replacement 123"),Map.of("currentPassword","Password 123","newPassword","Password 123")))mvc.perform(put("/api/auth/password").header("Authorization",first.access).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        assertThat(access(first)).isEqualTo(200);
        assertThat(sessions.findAll().getFirst().getRevokedAt()).isNull();
    }
}
