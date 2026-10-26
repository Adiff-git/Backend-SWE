package com.example.demo.auth.integration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class EmailLoginApiIT extends AuthPostgresTestSupport {
    @Test void login_shouldTrustOnlyCurrentEmailAndPreserveUsernameLogin() throws Exception {
        Pair owner=register("Alice");
        mvc.perform(put("/api/me").header("Authorization",owner.access).contentType("application/json").content(json.writeValueAsString(Map.of("email"," First@Example.com ")))).andExpect(status().isOk());
        login("FIRST@example.com",200);
        mvc.perform(put("/api/me").header("Authorization",owner.access).contentType("application/json").content(json.writeValueAsString(Map.of("email","second@example.com")))).andExpect(status().isOk());
        login("first@example.com",401);
        login("SECOND@example.com",200);
        login("ALICE",200);
    }
    private void login(String identifier,int expected) throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identifier",identifier,"password","Password 123")))).andExpect(status().is(expected));
    }
    @org.springframework.beans.factory.annotation.Autowired com.example.demo.user.service.UserService userService;
    @org.springframework.beans.factory.annotation.Autowired com.example.demo.auth.service.AuthService authService;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans= {
        false,true
    }
    )
    void login_shouldRecheckChangedIdentifierAfterWaitingForOwnerLock(boolean email) throws Exception {
        register("Alice");
        Long id=users.findAll().getFirst().getId();
        if(email) {
            var initial=new com.example.demo.user.dto.request.UpdateUserRequest();
            initial.setEmail("first@example.com");
            userService.updateUser(id,initial);
        }
        String oldIdentifier=email?"first@example.com":"Alice";
        String nextIdentifier=email?"second@example.com":"Renamed";
        var started=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var pending=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx-> {
                userService.getUserEntityForUpdate(id);var change=new com.example.demo.user.dto.request.UpdateUserRequest();if(email)change.setEmail(nextIdentifier);else change.setUsername(nextIdentifier);userService.updateUser(id,change);
                pending.set(pool.submit(()-> {
                    started.countDown();org.assertj.core.api.Assertions.assertThatThrownBy(()->authService.login(oldIdentifier,"Password 123")).isInstanceOf(com.example.demo.auth.exception.AuthenticationFailedException.class);
                }
                ));
                try {
                    if(!started.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Start timeout");long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);boolean waiting=false;while(System.nanoTime()<deadline) {
                        waiting=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND pid<>pg_backend_pid())",Boolean.class));if(waiting)break;Thread.sleep(20);
                    }
                    org.assertj.core.api.Assertions.assertThat(waiting).isTrue();
                }
                catch(InterruptedException failure) {
                    Thread.currentThread().interrupt();throw new IllegalStateException(failure);
                }
            }
            );
            pending.get().get(15,java.util.concurrent.TimeUnit.SECONDS);
        }
        login(nextIdentifier,200);
        org.assertj.core.api.Assertions.assertThat(sessions.count()).isEqualTo(2);
    }
}
