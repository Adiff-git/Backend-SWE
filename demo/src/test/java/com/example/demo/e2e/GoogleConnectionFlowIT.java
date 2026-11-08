package com.example.demo.e2e;

import com.example.demo.auth.integration.AuthPostgresTestSupport;
import com.example.demo.config.ControlledOidcProvider;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

class GoogleConnectionFlowIT extends AuthPostgresTestSupport {
    private static final ControlledOidcProvider PROVIDER=provider();
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.example.demo.auth.service.AuthSessionService sessionService;
    @org.springframework.beans.factory.annotation.Autowired com.example.demo.auth.service.InitialPasswordService initialPasswords;
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry registry){registry.add("security.identity.test-issuer",PROVIDER::issuer);}
    @AfterAll static void close(){PROVIDER.close();}
    @org.junit.jupiter.api.AfterEach void resetProvider(){PROVIDER.fault(ControlledOidcProvider.TokenFault.NONE);}
    @Test void localAccount_shouldConnectOnlyWithFreshProofAndOriginalSessionThenAllowGoogleLogin() throws Exception {
        Pair owner=register("Owner");
        PROVIDER.identity(Map.of("sub","linked-subject","email","owner@example.com","email_verified",true));
        String proof=passwordProof(owner,"CONNECT_GOOGLE");
        String handoff=handoff("/api/me/google-connection-attempts",Map.of("recentAuthProof",proof),owner);
        exchange(handoff,null,401);
        Pair another=pair(mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"identifier\":\"owner@example.com\",\"password\":\"Password 123\"}")).andExpect(status().isOk()).andReturn());
        exchange(handoff,another,401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM google_identities",Integer.class)).isZero();
        MvcResult linked=exchange(handoff,owner,200);assertThat(linked.getResponse().getHeader("Authorization")).isNull();
        assertThat(json.readTree(linked.getResponse().getContentAsString()).get("connected").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT consumed_at IS NOT NULL FROM recent_auth_proofs",Boolean.class)).isTrue();
        exchange(handoff,owner,401);
        Pair google=pair(exchange(handoff("/api/auth/google-login-attempts",Map.of(),null),null,200));
        assertThat(access(google)).isEqualTo(200);assertThat(users.count()).isOne();assertThat(users.findAll().getFirst().getEmail()).isEqualTo("owner@example.com");
    }
    @Test void socialAccount_shouldOptionallySetFirstPasswordAndRevokeEveryExistingSession() throws Exception {
        PROVIDER.identity(Map.of("sub","social-subject","email","social@example.com","email_verified",true));
        Pair first=pair(exchange(handoff("/api/auth/google-login-attempts",Map.of(),null),null,200));
        Pair second=pair(exchange(handoff("/api/auth/google-login-attempts",Map.of(),null),null,200));
        String stepup=handoff("/api/me/google-reauthentication-attempts",Map.of("operation","INITIAL_PASSWORD"),first);
        MvcResult result=exchange(stepup,first,200);assertThat(result.getResponse().getHeader("Authorization")).isNull();
        String proof=json.readTree(result.getResponse().getContentAsString()).get("recentAuthProof").asString();
        mvc.perform(post("/api/me/password").header("Authorization",first.access).contentType("application/json").content(json.writeValueAsString(Map.of("newPassword","Chosen 1234","recentAuthProof","z".repeat(43))))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/password").header("Authorization",first.access).contentType("application/json").content(json.writeValueAsString(Map.of("newPassword","short","recentAuthProof",proof)))).andExpect(status().isBadRequest());
        assertThat(access(first)).isEqualTo(200);assertThat(access(second)).isEqualTo(200);
        mvc.perform(post("/api/me/password").header("Authorization",first.access).contentType("application/json").content(json.writeValueAsString(Map.of("newPassword","Chosen 1234","recentAuthProof",proof))))
            .andExpect(status().isNoContent()).andExpect(header().doesNotExist("Authorization"));
        assertThat(access(first)).isEqualTo(401);assertThat(access(second)).isEqualTo(401);
        refresh(first.refresh).andExpect(status().isUnauthorized());refresh(second.refresh).andExpect(status().isUnauthorized());
        Pair local=pair(mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"identifier\":\"social@example.com\",\"password\":\"Chosen 1234\"}")).andExpect(status().isOk()).andReturn());
        mvc.perform(post("/api/me/password").header("Authorization",local.access).contentType("application/json").content(json.writeValueAsString(Map.of("newPassword","Other choice 456","recentAuthProof",proof)))).andExpect(status().isConflict());
        assertThat(access(local)).isEqualTo(200);assertThat(access(pair(exchange(handoff("/api/auth/google-login-attempts",Map.of(),null),null,200)))).isEqualTo(200);
    }
    @Test void failedDifferentIdentityLink_shouldNotConsumeProofOrCreatePartialAssociation() throws Exception {
        Pair owner=register("Owner"),other=register("Other");
        PROVIDER.identity(Map.of("sub","claimed-subject","email","owner@example.com","email_verified",true));
        exchange(handoff("/api/me/google-connection-attempts",Map.of("recentAuthProof",passwordProof(owner,"CONNECT_GOOGLE")),owner),owner,200);
        String otherProof=passwordProof(other,"CONNECT_GOOGLE");
        exchange(handoff("/api/me/google-connection-attempts",Map.of("recentAuthProof",otherProof),other),other,409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM google_identities",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT consumed_at IS NULL FROM recent_auth_proofs WHERE secret_hash=?",Boolean.class,identityProofs.hash(otherProof))).isTrue();
    }
    @Test void completion_shouldRejectExpiredProofAndConcurrentProofReuseWithoutPartialAssociation() throws Exception {
        Pair owner=register("Owner");PROVIDER.identity(Map.of("sub","proof-subject","email","owner@example.com","email_verified",true));
        String expired=passwordProof(owner,"CONNECT_GOOGLE");String oldHandoff=handoff("/api/me/google-connection-attempts",Map.of("recentAuthProof",expired),owner);
        jdbc.update("UPDATE recent_auth_proofs SET authenticated_at=authenticated_at-INTERVAL '6 minutes',expires_at=expires_at-INTERVAL '6 minutes' WHERE secret_hash=?",identityProofs.hash(expired));
        exchange(oldHandoff,owner,401);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM google_identities",Integer.class)).isZero();
        String proof=passwordProof(owner,"CONNECT_GOOGLE");String first=handoff("/api/me/google-connection-attempts",Map.of("recentAuthProof",proof),owner),second=handoff("/api/me/google-connection-attempts",Map.of("recentAuthProof",proof),owner);
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        try(java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            java.util.concurrent.Future<Integer> a=pool.submit(()->{start.await();return exchangeStatus(first,owner);});
            java.util.concurrent.Future<Integer> b=pool.submit(()->{start.await();return exchangeStatus(second,owner);});start.countDown();
            assertThat(java.util.List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,401);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM google_identities",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recent_auth_proofs WHERE consumed_at IS NOT NULL",Integer.class)).isOne();
    }
    @Test void initiation_shouldRejectWrongOwnerWrongOperationAndWrongSessionProof() throws Exception {
        Pair owner=register("Owner"),other=register("Other");String proof=passwordProof(owner,"CONNECT_GOOGLE"),wrongOperation=passwordProof(owner,"EMAIL_CHANGE");
        String shape="\"codeChallenge\":\""+Base64.getUrlEncoder().withoutPadding().encodeToString(identityProofs.hash("v".repeat(43)))+"\",\"codeChallengeMethod\":\"S256\",\"recentAuthProof\":\"";
        mvc.perform(post("/api/me/google-connection-attempts").header("Authorization",other.access).contentType("application/json").content("{"+shape+proof+"\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/google-connection-attempts").header("Authorization",owner.access).contentType("application/json").content("{"+shape+wrongOperation+"\"}")).andExpect(status().isUnauthorized());
        Pair another=pair(mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"identifier\":\"owner@example.com\",\"password\":\"Password 123\"}")).andExpect(status().isOk()).andReturn());
        mvc.perform(post("/api/me/google-connection-attempts").header("Authorization",another.access).contentType("application/json").content("{"+shape+proof+"\"}")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM google_identities",Integer.class)).isZero();
    }
    private int exchangeStatus(String code,Pair owner) throws Exception {
        return mvc.perform(post("/api/auth/google-code-exchanges").header("Authorization",owner.access).contentType("application/json").content(json.writeValueAsString(Map.of("code",code,"codeVerifier","v".repeat(43))))).andReturn().getResponse().getStatus();
    }
    @Test void googleOnlyEmailChange_shouldRequireLinkedFreshProofAndPermitReacquisitionBeforeOtpExpires() throws Exception {
        PROVIDER.identity(Map.of("sub","email-social-subject","email","original@example.com","email_verified",true));
        Pair owner=pair(exchange(handoff("/api/auth/google-login-attempts",Map.of(),null),null,200));
        String challenge=json.readTree(mvc.perform(post("/api/me/email-verification-challenges").header("Authorization",owner.access).contentType("application/json").content("{\"purpose\":\"EMAIL_CHANGE\",\"destination\":\"replacement@example.com\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        byte[] payload=jdbc.queryForObject("SELECT encrypted_payload FROM verification_mail_outbox WHERE challenge_id=?",byte[].class,java.util.UUID.fromString(challenge));
        String code=(String)json.readValue(identityProofs.decrypt(payload,challenge),Map.class).get("code");
        String firstProof=json.readTree(exchange(handoff("/api/me/google-reauthentication-attempts",Map.of("operation","EMAIL_CHANGE"),owner),owner,200).getResponse().getContentAsString()).get("recentAuthProof").asString();
        jdbc.update("UPDATE recent_auth_proofs SET authenticated_at=authenticated_at-INTERVAL '6 minutes',expires_at=expires_at-INTERVAL '6 minutes' WHERE secret_hash=?",identityProofs.hash(firstProof));
        mvc.perform(post("/api/me/email-verification-challenges/"+challenge+"/confirm").header("Authorization",owner.access).contentType("application/json").content(json.writeValueAsString(Map.of("code",code,"recentAuthProof",firstProof)))).andExpect(status().isUnauthorized());
        assertThat(users.findAll().getFirst().getEmail()).isEqualTo("original@example.com");
        String nextProof=json.readTree(exchange(handoff("/api/me/google-reauthentication-attempts",Map.of("operation","EMAIL_CHANGE"),owner),owner,200).getResponse().getContentAsString()).get("recentAuthProof").asString();
        mvc.perform(post("/api/me/email-verification-challenges/"+challenge+"/confirm").header("Authorization",owner.access).contentType("application/json").content(json.writeValueAsString(Map.of("code",code,"recentAuthProof",nextProof)))).andExpect(status().isOk());
        assertThat(users.findAll().getFirst().getEmail()).isEqualTo("replacement@example.com");assertThat(users.findAll().getFirst().getUsername()).isEqualTo("original");
        exchange(handoff("/api/auth/google-login-attempts",Map.of(),null),null,200);
        assertThat(users.findAll().getFirst().getEmail()).isEqualTo("replacement@example.com");assertThat(users.count()).isOne();
    }
    @Test void firstPasswordRaceWithRefresh_shouldSetOnceAndRevokeAnyRefreshedCredentials() throws Exception {
        PROVIDER.identity(Map.of("sub","password-race-subject","email","passwordrace@example.com","email_verified",true));
        Pair owner=pair(exchange(handoff("/api/auth/google-login-attempts",Map.of(),null),null,200));
        String proof=json.readTree(exchange(handoff("/api/me/google-reauthentication-attempts",Map.of("operation","INITIAL_PASSWORD"),owner),owner,200).getResponse().getContentAsString()).get("recentAuthProof").asString();
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        try(java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(3)){
            java.util.concurrent.Future<MvcResult> first=pool.submit(()->{start.await();return setPassword(owner,proof,"First choice 123");});
            java.util.concurrent.Future<MvcResult> second=pool.submit(()->{start.await();return setPassword(owner,proof,"Second choice 456");});
            java.util.concurrent.Future<MvcResult> refresh=pool.submit(()->{start.await();return refresh(owner.refresh).andReturn();});start.countDown();
            MvcResult a=first.get(20,java.util.concurrent.TimeUnit.SECONDS),b=second.get(20,java.util.concurrent.TimeUnit.SECONDS),rotated=refresh.get(20,java.util.concurrent.TimeUnit.SECONDS);
            java.util.List<Integer> statuses=java.util.List.of(a.getResponse().getStatus(),b.getResponse().getStatus());assertThat(statuses).contains(204);
            assertThat(statuses.stream().filter(value->value==204).count()).isOne();assertThat(statuses).allMatch(value->value==204 || value==401 || value==409);
            if(rotated.getResponse().getStatus()==200)assertThat(access(pair(rotated))).isEqualTo(401);else assertThat(rotated.getResponse().getStatus()).isEqualTo(401);
            String winner=a.getResponse().getStatus()==204?"First choice 123":"Second choice 456";
            mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identifier","passwordrace@example.com","password",winner)))).andExpect(status().isOk());
        }
        assertThat(access(owner)).isEqualTo(401);refresh(owner.refresh).andExpect(status().isUnauthorized());
    }
    @Test void sameLink_shouldBeIdempotentAndDifferentIdentityMustNeverReplaceIt() throws Exception {
        Pair owner=register("Owner");PROVIDER.identity(Map.of("sub","original-link-subject","email","owner@example.com","email_verified",true));
        exchange(handoff("/api/me/google-connection-attempts",Map.of("recentAuthProof",passwordProof(owner,"CONNECT_GOOGLE")),owner),owner,200);
        java.sql.Timestamp linkedAt=jdbc.queryForObject("SELECT linked_at FROM google_identities",java.sql.Timestamp.class);
        exchange(handoff("/api/me/google-connection-attempts",Map.of("recentAuthProof",passwordProof(owner,"CONNECT_GOOGLE")),owner),owner,200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM google_identities",Integer.class)).isOne();assertThat(jdbc.queryForObject("SELECT linked_at FROM google_identities",java.sql.Timestamp.class)).isEqualTo(linkedAt);
        PROVIDER.identity(Map.of("sub","replacement-link-subject","email","other@example.com","email_verified",true));
        exchange(handoff("/api/me/google-connection-attempts",Map.of("recentAuthProof",passwordProof(owner,"CONNECT_GOOGLE")),owner),owner,409);
        assertThat(jdbc.queryForObject("SELECT subject FROM google_identities",String.class)).isEqualTo("original-link-subject");
        mvc.perform(get("/api/me/google-connection").header("Authorization",owner.access)).andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("subject").doesNotExist()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("email").doesNotExist());
    }
    @Test void reauthentication_shouldRejectAnIdentityThatIsNotAlreadyLinkedToTheOwner() throws Exception {
        PROVIDER.identity(Map.of("sub","linked-social-subject","email","social@example.com","email_verified",true));Pair owner=pair(exchange(handoff("/api/auth/google-login-attempts",Map.of(),null),null,200));
        PROVIDER.identity(Map.of("sub","different-social-subject","email","social@example.com","email_verified",true));
        exchange(handoff("/api/me/google-reauthentication-attempts",Map.of("operation","INITIAL_PASSWORD"),owner),owner,401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recent_auth_proofs",Integer.class)).isZero();assertThat(access(owner)).isEqualTo(200);assertThat(users.findAll().getFirst().getPassword()).isNull();
    }
    private MvcResult setPassword(Pair owner,String proof,String password) throws Exception {
        return mvc.perform(post("/api/me/password").header("Authorization",owner.access).contentType("application/json").content(json.writeValueAsString(Map.of("newPassword",password,"recentAuthProof",proof)))).andReturn();
    }
    @Test void concurrentOwners_shouldNeverClaimTheSameGoogleIdentity() throws Exception {
        Pair owner=register("Owner"),other=register("Other");PROVIDER.identity(Map.of("sub","one-subject-two-owners","email","provider@example.com","email_verified",true));
        String first=handoff("/api/me/google-connection-attempts",Map.of("recentAuthProof",passwordProof(owner,"CONNECT_GOOGLE")),owner),second=handoff("/api/me/google-connection-attempts",Map.of("recentAuthProof",passwordProof(other,"CONNECT_GOOGLE")),other);
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        try(java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            java.util.concurrent.Future<Integer> a=pool.submit(()->{start.await();return exchangeStatus(first,owner);}),b=pool.submit(()->{start.await();return exchangeStatus(second,other);});start.countDown();
            assertThat(java.util.List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM google_identities",Integer.class)).isOne();assertThat(users.count()).isEqualTo(2);assertThat(sessions.count()).isEqualTo(2);
        assertThat(access(owner)).isEqualTo(200);assertThat(access(other)).isEqualTo(200);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=ControlledOidcProvider.TokenFault.class,names={"OLD_AUTH_TIME","MISSING_AUTH_TIME"})
    void reauthentication_shouldRequireActualFreshProviderAuthenticationTime(ControlledOidcProvider.TokenFault fault) throws Exception {
        PROVIDER.identity(Map.of("sub","freshness-subject","email","fresh@example.com","email_verified",true));Pair owner=pair(exchange(handoff("/api/auth/google-login-attempts",Map.of(),null),null,200));
        PROVIDER.fault(fault);String destination=callbackDestination("/api/me/google-reauthentication-attempts",Map.of("operation","INITIAL_PASSWORD"),owner);
        assertThat(destination).startsWith("http://localhost:3000/auth/failure?error=");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recent_auth_proofs",Integer.class)).isZero();
        assertThat(sessions.count()).isOne();assertThat(access(owner)).isEqualTo(200);
    }
    @Test void completion_shouldRequireCurrentProfilePermissionAndPreserveProofWhenPermissionWasRevoked() throws Exception {
        Pair owner=register("Owner");PROVIDER.identity(Map.of("sub","permission-subject","email","owner@example.com","email_verified",true));String proof=passwordProof(owner,"CONNECT_GOOGLE");
        String code=handoff("/api/me/google-connection-attempts",Map.of("recentAuthProof",proof),owner);jdbc.update("DELETE FROM user_roles WHERE user_id=?",users.findAll().getFirst().getId());
        exchange(code,owner,403);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM google_identities",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT consumed_at IS NULL FROM recent_auth_proofs",Boolean.class)).isTrue();
    }
    @Test void initialPasswordRevocationFailure_shouldRollbackPasswordProofAndEverySession() throws Exception {
        PROVIDER.identity(Map.of("sub","rollback-password-subject","email","rollbackpassword@example.com","email_verified",true));Pair owner=pair(exchange(handoff("/api/auth/google-login-attempts",Map.of(),null),null,200));
        String proof=json.readTree(exchange(handoff("/api/me/google-reauthentication-attempts",Map.of("operation","INITIAL_PASSWORD"),owner),owner,200).getResponse().getContentAsString()).get("recentAuthProof").asString();
        org.mockito.Mockito.doAnswer(call->{call.callRealMethod();throw new IllegalStateException("Controlled revocation failure");}).when(sessionService).revokeAll(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.any());
        org.assertj.core.api.Assertions.assertThatThrownBy(()->initialPasswords.set(users.findAll().getFirst().getId(),sessions.findAll().getFirst().getId(),"Chosen 1234",proof)).isInstanceOf(IllegalStateException.class);
        assertThat(users.findAll().getFirst().getPassword()).isNull();assertThat(jdbc.queryForObject("SELECT consumed_at IS NULL FROM recent_auth_proofs",Boolean.class)).isTrue();assertThat(sessions.findAll()).allMatch(session->session.getRevokedAt()==null);assertThat(access(owner)).isEqualTo(200);
    }
    private String passwordProof(Pair owner,String operation) throws Exception {
        return json.readTree(mvc.perform(post("/api/me/reauthentications").header("Authorization",owner.access).contentType("application/json").content(json.writeValueAsString(Map.of("operation",operation,"password","Password 123")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("recentAuthProof").asString();
    }
    private String handoff(String path,Map<String,String> extra,Pair owner) throws Exception {
        String destination=callbackDestination(path,extra,owner);
        assertThat(destination).contains("/auth/success?code=");return destination.substring(destination.indexOf("?code=")+6);
    }
    private String callbackDestination(String path,Map<String,String> extra,Pair owner) throws Exception {
        Map<String,String> body=new LinkedHashMap<>(extra);body.put("codeChallenge",Base64.getUrlEncoder().withoutPadding().encodeToString(identityProofs.hash("v".repeat(43))));body.put("codeChallengeMethod","S256");
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request=post(path).contentType("application/json").content(json.writeValueAsString(body));if(owner!=null)request.header("Authorization",owner.access);
        MvcResult start=mvc.perform(request).andExpect(status().isCreated()).andReturn();String setCookie=start.getResponse().getHeader("Set-Cookie");Cookie cookie=new Cookie("google_handshake",setCookie.substring(setCookie.indexOf('=')+1,setCookie.indexOf(';')));
        String authorization=json.readTree(start.getResponse().getContentAsString()).get("authorizationUrl").asString();String provider=mvc.perform(get(URI.create(authorization)).cookie(cookie)).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        if(path.contains("reauthentication"))assertThat(provider).contains("max_age=0");
        String callback=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(provider)).GET().build(),HttpResponse.BodyHandlers.discarding()).headers().firstValue("Location").orElseThrow();
        String destination=mvc.perform(get(URI.create(callback)).cookie(cookie)).andExpect(status().isFound()).andExpect(header().doesNotExist("Set-Cookie")).andReturn().getResponse().getHeader("Location");
        return destination;
    }
    private MvcResult exchange(String code,Pair owner,int expected) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request=post("/api/auth/google-code-exchanges").contentType("application/json").content(json.writeValueAsString(Map.of("code",code,"codeVerifier","v".repeat(43))));if(owner!=null)request.header("Authorization",owner.access);
        return mvc.perform(request).andExpect(status().is(expected)).andReturn();
    }
    private static ControlledOidcProvider provider(){try{return new ControlledOidcProvider(Clock.systemUTC());}catch(Exception failure){throw new ExceptionInInitializerError(failure);}}
}
