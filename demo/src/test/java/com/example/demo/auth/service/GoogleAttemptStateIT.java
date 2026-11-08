package com.example.demo.auth.service;

import com.example.demo.auth.entity.OAuthLoginAttempt;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.repository.OAuthHandoffRepository;
import com.example.demo.auth.repository.OAuthLoginAttemptRepository;
import com.example.demo.user.integration.PostgresTestSupport;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest @ActiveProfiles({"test","identity-test"})
class GoogleAttemptStateIT extends PostgresTestSupport {
    @Autowired GoogleAttemptService attempts;
    @Autowired GoogleHandoffService handoffs;
    @Autowired IdentityProofService proofs;
    @Autowired OAuthLoginAttemptRepository repository;
    @Autowired OAuthHandoffRepository handoffRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    private final String verifier="v".repeat(43);
    @AfterEach void cleanup(){
        jdbc.update("DELETE FROM oauth_handoffs");
        jdbc.update("UPDATE oauth_browser_slots SET current_attempt_id=NULL");
        jdbc.update("DELETE FROM oauth_login_attempts");jdbc.update("DELETE FROM oauth_browser_slots");
    }
    @Test void authorization_shouldPersistAuthenticatedStateAndConsumeItOnce() {
        GoogleAttemptStart start=start(null);
        save(start,"state-one");
        GoogleAuthorizationState restored=attempts.loadAuthorization("state-one",start.getCookie(),false);
        assertThat(restored.getRequest().getAttributes()).containsEntry("nonce","nonce-value").containsEntry("code_verifier",verifier);
        assertThat(restored.getRequest().getAdditionalParameters()).containsEntry("nonce","nonce-hash");
        assertThat(attempts.loadAuthorization("state-one","wrong-cookie",false)).isNull();
        assertThat(attempts.loadAuthorization("state-one",start.getCookie(),true)).isNotNull();
        assertThat(attempts.loadAuthorization("state-one",start.getCookie(),true)).isNull();
        assertThat(repository.findById(restored.getAttemptId()).orElseThrow().getEncryptedRequest()).isNull();
    }
    @Test void supersededAttempt_shouldRejectOldCallbackWithoutInvalidatingNewCookie() {
        GoogleAttemptStart old=start(null);save(old,"old-state");
        GoogleAttemptStart latest=start(old.getCookie());save(latest,"latest-state");
        assertThat(attempts.loadAuthorization("old-state",old.getCookie(),true)).isNull();
        assertThat(attempts.loadAuthorization("old-state",latest.getCookie(),true)).isNull();
        assertThat(attempts.loadAuthorization("latest-state",old.getCookie(),false)).isNull();
        assertThat(attempts.loadAuthorization("latest-state",latest.getCookie(),true)).isNotNull();
    }
    @Test void handoff_shouldRequirePkceAndAllowOnlyOneRedemptionEvenWhileCookieExists() {
        GoogleAttemptStart start=start(null);save(start,"handoff-state");
        UUID id=attempts.loadAuthorization("handoff-state",start.getCookie(),true).getAttemptId();
        String code=attempts.complete(id,new ValidatedGoogleIdentity("subject","mail@example.test",true,"Name",Instant.now()));
        UUID handoff=handoffRepository.findByCodeHash(proofs.hash(code)).orElseThrow().getId();
        assertThatThrownBy(()->new TransactionTemplate(manager).execute(s->handoffs.lockForRedemption(handoff,proofs.hash(code),"w".repeat(43)))).isInstanceOf(IdentityException.class);
        new TransactionTemplate(manager).executeWithoutResult(s->handoffs.consume(handoffs.lockForRedemption(handoff,proofs.hash(code),verifier)));
        assertThatThrownBy(()->new TransactionTemplate(manager).execute(s->handoffs.lockForRedemption(handoff,proofs.hash(code),verifier))).isInstanceOf(IdentityException.class);
        assertThat(attempts.loadAuthorization("handoff-state",start.getCookie(),true)).isNull();
    }
    @Test void overlappingInitiationsWithSameEarlierCookie_shouldShareSlotAndRequireRestartAfterOutOfOrderResponse(){
        GoogleAttemptStart initial=start(null);
        GoogleAttemptStart older=start(initial.getCookie());GoogleAttemptStart newer=start(initial.getCookie());
        assertThat(older.getCookie().substring(0,36)).isEqualTo(newer.getCookie().substring(0,36));
        assertThatThrownBy(()->attempts.authorizationPurpose(older.getTicket(),older.getCookie())).isInstanceOf(IdentityException.class);
        assertThatThrownBy(()->attempts.authorizationPurpose(newer.getTicket(),older.getCookie())).isInstanceOf(IdentityException.class);
        GoogleAttemptStart recovered=start(older.getCookie());assertThat(recovered.getCookie().substring(0,36)).isEqualTo(newer.getCookie().substring(0,36));
        assertThat(attempts.authorizationPurpose(recovered.getTicket(),recovered.getCookie())).isEqualTo(OAuthLoginAttempt.Purpose.LOGIN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oauth_login_attempts WHERE status='CREATED'",Integer.class)).isOne();
    }
    @Test void failedCallbackCorrelation_shouldInvalidateOnlyItsOwnAttemptAndNeverPermitLaterReplay(){
        GoogleAttemptStart initial=start(null);save(initial,"missing-cookie-state");
        assertThat(attempts.loadAuthorization("missing-cookie-state",null,true)).isNull();
        assertThat(attempts.loadAuthorization("missing-cookie-state",initial.getCookie(),true)).isNull();
        GoogleAttemptStart restarted=start(initial.getCookie());save(restarted,"restarted-state");
        assertThat(attempts.loadAuthorization("missing-cookie-state",restarted.getCookie(),true)).isNull();
        assertThat(attempts.loadAuthorization("restarted-state",restarted.getCookie(),true)).isNotNull();
    }
    @Test void callbackCompletionRacingNewInitiation_shouldUseConsistentSlotThenAttemptLocks() throws Exception {
        GoogleAttemptStart original=start(null);save(original,"concurrent-callback");UUID id=attempts.loadAuthorization("concurrent-callback",original.getCookie(),true).getAttemptId();
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        try(java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            java.util.concurrent.Future<String> callback=pool.submit(()->{start.await();try{return attempts.complete(id,new ValidatedGoogleIdentity("subject","race@example.com",true,"Name",Instant.now()));}catch(IdentityException invalid){return null;}});
            java.util.concurrent.Future<GoogleAttemptStart> initiation=pool.submit(()->{start.await();return start(original.getCookie());});start.countDown();
            callback.get(20,java.util.concurrent.TimeUnit.SECONDS);GoogleAttemptStart latest=initiation.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(attempts.authorizationPurpose(latest.getTicket(),latest.getCookie())).isEqualTo(OAuthLoginAttempt.Purpose.LOGIN);
            assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(OAuthLoginAttempt.Status.FAILED);
        }
    }
    private GoogleAttemptStart start(String cookie){
        return attempts.start(Base64.getUrlEncoder().withoutPadding().encodeToString(proofs.hash(verifier)),"S256",cookie,OAuthLoginAttempt.Purpose.LOGIN,null,null,null,null);
    }
    private void save(GoogleAttemptStart start,String state){
        attempts.saveAuthorization(start.getTicket(),start.getCookie(),OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .clientId("controlled-client").redirectUri("http://localhost:8080/login/oauth2/code/google").scopes(java.util.Set.of("openid","email","profile")).state(state)
            .attributes(Map.of("nonce","nonce-value","registration_id","google","code_verifier",verifier)).additionalParameters(Map.of("nonce","nonce-hash")).build());
    }
}
