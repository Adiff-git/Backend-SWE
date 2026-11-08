package com.example.demo.auth.service;

import com.example.demo.auth.dto.response.PendingRegistrationResponse;
import com.example.demo.auth.entity.OAuthLoginAttempt;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.integration.AuthPostgresTestSupport;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import static org.assertj.core.api.Assertions.assertThat;

class GoogleLoginConcurrencyIT extends AuthPostgresTestSupport {
    @Autowired GoogleAttemptService attempts;
    @Autowired GoogleExchangeService exchanges;
    @Autowired PendingRegistrationService registrations;
    @Autowired Clock clock;
    private final String verifier="v".repeat(43);
    @Test void simultaneousFirstLogin_shouldCreateOneAssociationWithoutOrphanSessions() throws Exception {
        String first=handoff("same-subject","race@example.com"),second=handoff("same-subject","race@example.com");
        List<Integer> outcomes=race(()->exchange(first),()->exchange(second));
        assertThat(outcomes).allMatch(value->value==200 || value==409).contains(200);
        assertThat(users.count()).isOne();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM google_identities",Integer.class)).isOne();
        long successful=outcomes.stream().filter(value->value==200).count();assertThat(sessions.count()).isEqualTo(successful);assertThat(credentials.count()).isEqualTo(successful);
    }
    @Test void pendingConfirmationVersusGoogleRegistration_shouldHaveOneWinnerAndNoAuthenticationFromCollision() throws Exception {
        PendingRegistrationResponse pending=registrations.initiate("claim@example.com","Never activate 123");
        byte[] encrypted=jdbc.queryForObject("SELECT encrypted_payload FROM verification_mail_outbox WHERE challenge_id=?",byte[].class,pending.getChallengeId());
        String otp=(String)json.readValue(identityProofs.decrypt(encrypted,pending.getChallengeId().toString()),Map.class).get("code");
        String google=handoff("google-claim-subject","claim@example.com");
        List<Integer> outcomes=race(()->{try{registrations.confirm(pending.getRegistrationCredential(),pending.getChallengeId(),otp,"Authoritative 456");return 201;}catch(IdentityException failure){return failure.getStatus();}},()->exchange(google));
        assertThat(outcomes).isEqualTo(outcomes.getFirst()==201?List.of(201,409):List.of(409,200));
        assertThat(users.count()).isOne();assertThat(sessions.count()).isOne();assertThat(credentials.count()).isOne();
        if(outcomes.getFirst()==201)assertThat(users.findAll().getFirst().getPassword()).isNotNull();else assertThat(users.findAll().getFirst().getPassword()).isNull();
    }
    @Test void independentPendingConfirmations_shouldCreateOnlyOneAccountForSharedDestination() throws Exception {
        PendingRegistrationResponse first=registrations.initiate("two-pending@example.com","Initial first 123");
        PendingRegistrationResponse second=registrations.initiate("two-pending@example.com","Initial second 456");String firstCode=registrationCode(first),secondCode=registrationCode(second);
        List<Integer> outcomes=race(()->confirm(first,firstCode),()->confirm(second,secondCode));assertThat(outcomes).containsExactlyInAnyOrder(201,409);
        assertThat(users.count()).isOne();assertThat(sessions.count()).isOne();assertThat(credentials.count()).isOne();
    }
    private String registrationCode(PendingRegistrationResponse pending){byte[] payload=jdbc.queryForObject("SELECT encrypted_payload FROM verification_mail_outbox WHERE challenge_id=?",byte[].class,pending.getChallengeId());return (String)json.readValue(identityProofs.decrypt(payload,pending.getChallengeId().toString()),Map.class).get("code");}
    private int confirm(PendingRegistrationResponse pending,String code){try{registrations.confirm(pending.getRegistrationCredential(),pending.getChallengeId(),code,"Final 1234");return 201;}catch(IdentityException failure){return failure.getStatus();}}
    private List<Integer> race(Callable<Integer> first,Callable<Integer> second) throws Exception {
        CountDownLatch start=new CountDownLatch(1);
        try(ExecutorService pool=Executors.newFixedThreadPool(2)){
            Future<Integer> a=pool.submit(()->{start.await();return first.call();}),b=pool.submit(()->{start.await();return second.call();});start.countDown();return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
    private int exchange(String code){try{exchanges.exchange(code,verifier,null);return 200;}catch(IdentityException failure){return failure.getStatus();}}
    private String handoff(String subject,String email){
        GoogleAttemptStart start=attempts.start(Base64.getUrlEncoder().withoutPadding().encodeToString(identityProofs.hash(verifier)),"S256",null,OAuthLoginAttempt.Purpose.LOGIN,null,null,null,null);String state=UUID.randomUUID().toString();
        attempts.saveAuthorization(start.getTicket(),start.getCookie(),OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://accounts.google.com/o/oauth2/v2/auth").clientId("controlled-client").redirectUri("http://localhost:8080/login/oauth2/code/google").scopes(java.util.Set.of("openid","email","profile")).state(state).attributes(Map.of("nonce","nonce-value")).build());
        UUID id=attempts.loadAuthorization(state,start.getCookie(),true).getAttemptId();return attempts.complete(id,new ValidatedGoogleIdentity(subject,email,true,"Name",clock.instant()));
    }
}
