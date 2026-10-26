package com.example.demo.auth.integration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.demo.auth.service.AuthService;
import com.example.demo.auth.service.TokenService;
import com.example.demo.auth.exception.AuthenticationFailedException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class RefreshRotationApiIT extends AuthPostgresTestSupport {
    @Autowired AuthService auth;
    @Autowired TokenService tokens;
    @Test void refresh_shouldRotateWithoutExtendingExpiryAndCommitReuseRevocationBefore401() throws Exception {
        Pair first=register("Alice");
        Pair other=pair(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("identifier","Alice","password","Password 123")))).andExpect(status().isOk()).andReturn());
        java.time.Instant original=sessions.findAll().getFirst().getExpiresAt();
        Pair replacement=pair(refresh(first.refresh).andExpect(status().isOk()).andReturn());
        assertThat(access(replacement)).isEqualTo(200);
        refresh(first.refresh).andExpect(status().isUnauthorized());
        assertThat(access(first)).isEqualTo(401);
        assertThat(access(replacement)).isEqualTo(401);
        assertThat(access(other)).isEqualTo(200);
        assertThat(sessions.findAll().stream().filter(s->s.getRevokedAt()!=null).count()).isEqualTo(1);
        assertThat(sessions.findAll().stream().map(s->s.getExpiresAt())).contains(original);
    }
    @Test void refresh_shouldNotMutateSessionForUnknownCredential() throws Exception {
        Pair pair=register("Alice");
        refresh("unknown").andExpect(status().isUnauthorized());
        assertThat(access(pair)).isEqualTo(200);
        assertThat(credentials.count()).isEqualTo(1);
    }
    @Test void refresh_shouldRevokeOnConcurrentSameCredentialReuse() throws Exception {
        Pair first=register("Alice");
        CountDownLatch start=new CountDownLatch(1);
        try(java.util.concurrent.ExecutorService executor=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> attempt=()-> {
                if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Barrier timeout");
                try {
                    auth.refresh(first.refresh);
                    return 200;
                }
                catch(AuthenticationFailedException expected) {
                    return 401;
                }
            }
            ;
            java.util.concurrent.Future<Integer> a=executor.submit(attempt);
            java.util.concurrent.Future<Integer> b=executor.submit(attempt);
            start.countDown();
            assertThat(java.util.List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,401);
        }
        assertThat(access(first)).isEqualTo(401);
        assertThat(sessions.findAll().getFirst().getRevokedAt()).isNotNull();
    }
}
