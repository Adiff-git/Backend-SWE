package com.example.demo.auth.integration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.demo.auth.service.AuthService;
import com.example.demo.auth.exception.AuthenticationFailedException;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class LogoutRefreshRaceIT extends AuthPostgresTestSupport {
    @Autowired AuthService auth;
    @Test void execute_shouldVerifyConcurrentRefreshCannotLeaveLiveCredentialAfterLogout() throws Exception {
        Pair p=register("Alice");
        CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var refresh=pool.submit(()-> {
                start.await();try {
                    return auth.refresh(p.refresh);
                }
                catch(AuthenticationFailedException rejected) {
                    return null;
                }
            }
            );
            var logout=pool.submit(()-> {
                start.await();auth.logout(p.refresh);return true;
            }
            );
            start.countDown();
            var replacement=refresh.get(20,TimeUnit.SECONDS);
            assertThat(logout.get(20,TimeUnit.SECONDS)).isTrue();
            assertThat(access(p)).isEqualTo(401);
            if(replacement!=null) {
                assertThat(access(new Pair("Bearer "+replacement.getAccessToken(),replacement.getResponse().getRefreshToken()))).isEqualTo(401);
                assertThatThrownBy(()->auth.refresh(replacement.getResponse().getRefreshToken())).isInstanceOf(AuthenticationFailedException.class);
            }
        }
    }
}
