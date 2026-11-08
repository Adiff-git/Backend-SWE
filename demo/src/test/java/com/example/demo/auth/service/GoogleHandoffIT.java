package com.example.demo.auth.service;

import com.example.demo.auth.entity.OAuthLoginAttempt;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.integration.AuthPostgresTestSupport;
import com.example.demo.auth.repository.OAuthHandoffRepository;
import com.example.demo.config.MutableClock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(IdentityCleanupIT.TimeConfiguration.class)
class GoogleHandoffIT extends AuthPostgresTestSupport {
    @Autowired MutableClock clock;
    @Autowired GoogleAttemptService attempts;
    @Autowired GoogleHandoffService handoffs;
    @Autowired GoogleExchangeService exchanges;
    @Autowired OAuthHandoffRepository handoffRepository;
    @Autowired PlatformTransactionManager manager;
    private final String verifier="v".repeat(43);
    @BeforeEach void resetClock(){clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));}
    @Test void redemption_shouldAcceptImmediatelyBeforeAndRejectExactlyAtSixtySeconds(){
        String code=code();com.example.demo.auth.entity.OAuthHandoff row=handoffRepository.findByCodeHash(identityProofs.hash(code)).orElseThrow();
        clock.set(row.getExpiresAt().minusNanos(1));
        com.example.demo.auth.entity.OAuthHandoff valid=new TransactionTemplate(manager).execute(tx->handoffs.lockForRedemption(row.getId(),identityProofs.hash(code),verifier));
        assertThat(valid).isNotNull();
        clock.set(row.getExpiresAt());assertThatThrownBy(()->exchanges.exchange(code,verifier,null)).isInstanceOf(IdentityException.class);
        assertThat(users.count()).isZero();assertThat(sessions.count()).isZero();
    }
    @Test void redemption_shouldRequirePkceAndCommitExactlyOneConcurrentSession() throws Exception {
        String code=code();assertThatThrownBy(()->exchanges.exchange(code,"w".repeat(43),null)).isInstanceOf(IdentityException.class);
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        try(java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            java.util.concurrent.Callable<Integer> redeem=()->{start.await();try{exchanges.exchange(code,verifier,null);return 200;}catch(IdentityException failure){return failure.getStatus();}};
            java.util.concurrent.Future<Integer> a=pool.submit(redeem),b=pool.submit(redeem);start.countDown();
            assertThat(java.util.List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,401);
        }
        assertThat(users.count()).isOne();assertThat(sessions.count()).isOne();assertThat(credentials.count()).isOne();
    }
    @Test void authorization_shouldRejectExpiredCookieAndPermitSafeRestart(){
        GoogleAttemptStart first=start(null);clock.set(first.getExpiresAt());
        assertThatThrownBy(()->attempts.authorizationPurpose(first.getTicket(),first.getCookie())).isInstanceOf(IdentityException.class);
        GoogleAttemptStart restarted=start(first.getCookie());assertThat(restarted.getCookie()).isNotEqualTo(first.getCookie());
        assertThat(attempts.authorizationPurpose(restarted.getTicket(),restarted.getCookie())).isEqualTo(OAuthLoginAttempt.Purpose.LOGIN);
    }
    @Autowired IdentityCleanupService cleanupService;
    @Test void cleanup_shouldEraseHandoffAtItsOwnRetentionDeadlineEvenBeforeParentAttemptDeadline(){
        String code=code();com.example.demo.auth.entity.OAuthHandoff row=handoffRepository.findByCodeHash(identityProofs.hash(code)).orElseThrow();
        clock.set(row.getExpiresAt().plusSeconds(86400));cleanupService.cleanup();
        assertThat(handoffRepository.count()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oauth_login_attempts",Integer.class)).isOne();
    }
    private GoogleAttemptStart start(String cookie){return attempts.start(Base64.getUrlEncoder().withoutPadding().encodeToString(identityProofs.hash(verifier)),"S256",cookie,OAuthLoginAttempt.Purpose.LOGIN,null,null,null,null);}
    private String code(){
        GoogleAttemptStart start=start(null);String state=UUID.randomUUID().toString();
        attempts.saveAuthorization(start.getTicket(),start.getCookie(),OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://accounts.google.com/o/oauth2/v2/auth").clientId("controlled-client").redirectUri("http://localhost:8080/login/oauth2/code/google").scopes(java.util.Set.of("openid","email","profile")).state(state).attributes(Map.of("nonce","nonce-value")).build());
        UUID id=attempts.loadAuthorization(state,start.getCookie(),true).getAttemptId();
        return attempts.complete(id,new ValidatedGoogleIdentity("handoff-subject","handoff@example.com",true,"Name",clock.instant()));
    }
}
