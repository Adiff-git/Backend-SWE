package com.example.demo.auth.integration;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Import(AuthSessionExpiryApiIT.TimeConfiguration.class)
class AuthSessionExpiryApiIT extends AuthPostgresTestSupport {
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary MutableClock testClock() {
            return new MutableClock();
        }
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> time=new AtomicReference<>();
        public Instant instant() {
            return time.get();
        }
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }
        public Clock withZone(ZoneId zone) {
            return this;
        }
        void advance(long seconds) {
            time.updateAndGet(t->t.plusSeconds(seconds));
        }
    }
    @Autowired MutableClock clock;
    @BeforeEach void resetClock() {
        clock.time.set(Instant.parse("2026-10-26T00:00:00Z"));
    }
    @Test void execute_shouldVerifyExpiredAccessStillAllowsRefreshCredentialLogout() throws Exception {
        Pair p=register("Alice");
        clock.advance(900);
        assertThat(access(p)).isEqualTo(401);
        mvc.perform(post("/api/auth/logout").header("Authorization",p.access).contentType("application/json").content(json.writeValueAsString(java.util.Map.of("refreshToken",p.refresh)))).andExpect(status().isNoContent());
    }
    @Test void execute_shouldVerifyRefreshCapsAccessAtOriginalDeadlineAndRejectsEquality() throws Exception {
        Pair p=register("Alice");
        clock.advance(604750);
        var response=refresh(p.refresh).andExpect(status().isOk()).andExpect(jsonPath("accessTokenExpiresIn").value(50)).andReturn();
        Pair next=pair(response);
        assertThat(access(next)).isEqualTo(200);
        clock.advance(50);
        assertThat(access(next)).isEqualTo(401);
        refresh(next.refresh).andExpect(status().isUnauthorized());
    }
}
