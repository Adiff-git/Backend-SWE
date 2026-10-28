package com.example.demo.auth.integration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.demo.auth.service.AuthService;
import com.example.demo.auth.exception.AuthenticationFailedException;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class PasswordRevocationRaceIT extends AuthPostgresTestSupport {
    @Autowired AuthService auth;
    @Test void execute_shouldVerifyRefreshAndOldPasswordLoginCannotSurviveCommittedPasswordChange() throws Exception {
        Pair p=register("Alice");
        var session=sessions.findAll().getFirst();
        CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(3)) {
            var refresh=pool.submit(()-> {
                start.await();try {
                    return auth.refresh(p.refresh);
                }
                catch(AuthenticationFailedException rejected) {
                    return null;
                }
            }
            );
            var login=pool.submit(()-> {
                start.await();try {
                    return auth.login("Alice","Password 123");
                }
                catch(AuthenticationFailedException rejected) {
                    return null;
                }
            }
            );
            var change=pool.submit(()-> {
                start.await();auth.changePassword(session.getUserId(),session.getId(),"Password 123","Replacement 123");return true;
            }
            );
            start.countDown();
            assertThat(change.get(30,TimeUnit.SECONDS)).isTrue();
            for(var task:java.util.List.of(refresh,login)) {
                var pair=task.get(30,TimeUnit.SECONDS);
                if(pair!=null)assertThat(access(new Pair("Bearer "+pair.getAccessToken(),pair.getResponse().getRefreshToken()))).isEqualTo(401);
            }
            assertThat(access(p)).isEqualTo(401);
            assertThatThrownBy(()->auth.login("Alice","Password 123")).isInstanceOf(AuthenticationFailedException.class);
            assertThat(auth.login("Alice","Replacement 123")).isNotNull();
        }
    }
}
