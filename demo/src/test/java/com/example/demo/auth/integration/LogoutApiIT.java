package com.example.demo.auth.integration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class LogoutApiIT extends AuthPostgresTestSupport {
    @Test void logout_shouldAcceptRetainedConsumedCredentialAndBeIdempotent() throws Exception {
        Pair initial=register("Alice");
        Pair replacement=pair(refresh(initial.refresh).andExpect(status().isOk()).andReturn());
        for(int i=0;i<2;i++)mvc.perform(post("/api/auth/logout").header("Authorization","Bearer expired-unrelated").contentType("application/json").content(json.writeValueAsString(Map.of("refreshToken",initial.refresh)))).andExpect(status().isNoContent());
        assertThat(access(replacement)).isEqualTo(401);
        refresh(replacement.refresh).andExpect(status().isUnauthorized());
    }
    @Test void logout_shouldRejectUnknownOrExpiredCredentialIndependentlyOfCleanup() throws Exception {
        Pair initial=register("Alice");
        jdbc.update("UPDATE authentication_sessions SET created_at=created_at-INTERVAL '8 days',expires_at=expires_at-INTERVAL '8 days'");
        for(String token:java.util.List.of(initial.refresh,"unknown"))mvc.perform(post("/api/auth/logout").contentType("application/json").content(json.writeValueAsString(Map.of("refreshToken",token)))).andExpect(status().isUnauthorized());
    }
}
