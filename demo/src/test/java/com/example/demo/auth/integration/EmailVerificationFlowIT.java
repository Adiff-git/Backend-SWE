package com.example.demo.auth.integration;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class EmailVerificationFlowIT extends AuthPostgresTestSupport {
    @Test void replacement_shouldRequireFreshOwnerProofAndPreserveOfficialEmailUntilConfirmation() throws Exception {
        Pair owner=register("Alice");
        String challenge=json.readTree(mvc.perform(post("/api/me/email-verification-challenges").header("Authorization",owner.access).contentType("application/json").content("{\"purpose\":\"EMAIL_CHANGE\",\"destination\":\"next@example.com\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        assertThat(users.findAll().getFirst().getEmail()).isEqualTo("alice@example.com");
        String code=code(challenge);
        mvc.perform(post("/api/me/email-verification-challenges/"+challenge+"/confirm").header("Authorization",owner.access).contentType("application/json").content(json.writeValueAsString(Map.of("code",code)))).andExpect(status().isUnauthorized());
        String proof=json.readTree(mvc.perform(post("/api/me/reauthentications").header("Authorization",owner.access).contentType("application/json").content("{\"operation\":\"EMAIL_CHANGE\",\"password\":\"Password 123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("recentAuthProof").asString();
        mvc.perform(post("/api/me/email-verification-challenges/"+challenge+"/confirm").header("Authorization",owner.access).contentType("application/json").content(json.writeValueAsString(Map.of("code",code,"recentAuthProof",proof))))
            .andExpect(status().isOk()).andExpect(jsonPath("email").value("next@example.com")).andExpect(jsonPath("emailVerified").value(true));
        mvc.perform(get("/api/me").header("Authorization",owner.access)).andExpect(status().isOk()).andExpect(jsonPath("hasLocalPassword").value(true)).andExpect(jsonPath("emailVerified").value(true));
        assertThat(users.findAll().getFirst().getUsername()).isEqualTo("alice");
        assertThat(jdbc.queryForObject("SELECT consumed_at IS NOT NULL FROM recent_auth_proofs",Boolean.class)).isTrue();
    }
    @Test void profile_shouldRejectEverySuppliedEmailWhileOmissionPreservesIt() throws Exception {
        Pair owner=register("Owner");
        for(String body:java.util.List.of("{\"email\":null}","{\"email\":\"\"}","{\"email\":\"next@example.com\"}"))
            mvc.perform(put("/api/me").header("Authorization",owner.access).contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("EMAIL_VERIFICATION_REQUIRED"));
        mvc.perform(put("/api/me").header("Authorization",owner.access).contentType("application/json").content("{\"displayName\":\"New name\"}")).andExpect(status().isOk()).andExpect(jsonPath("email").value("owner@example.com"));
    }
    @Test void challenge_shouldHideOtherOwnersAndWrongPasswordsShouldNotLockOutValidProof() throws Exception {
        Pair owner=register("Owner"),other=register("Other");
        String challenge=json.readTree(mvc.perform(post("/api/me/email-verification-challenges").header("Authorization",owner.access).contentType("application/json").content("{\"purpose\":\"EMAIL_CHANGE\",\"destination\":\"next@example.com\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        mvc.perform(get("/api/me/email-verification-challenges/"+challenge).header("Authorization",other.access)).andExpect(status().isNotFound());
        for(int i=0;i<12;i++)mvc.perform(post("/api/me/reauthentications").header("Authorization",owner.access).contentType("application/json").content("{\"operation\":\"EMAIL_CHANGE\",\"password\":\"Wrong password\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/reauthentications").header("Authorization",owner.access).contentType("application/json").content("{\"operation\":\"EMAIL_CHANGE\",\"password\":\"Password 123\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recent_auth_proofs",Integer.class)).isOne();
    }
    @Test void wrongCodes_shouldAllowValidCodeAfterRepeatedFailuresWithoutChangingOfficialEmailOrConsumingAccountProof() throws Exception {
        Pair owner=register("Owner");String challenge=createChallenge(owner,"next@example.com"),proof=accountProof(owner),actual=code(challenge),wrong="000000".equals(actual)?"000001":"000000";
        for(int index=0;index<12;index++)mvc.perform(post("/api/me/email-verification-challenges/"+challenge+"/confirm").header("Authorization",owner.access).contentType("application/json").content(json.writeValueAsString(Map.of("code",wrong,"recentAuthProof",proof)))).andExpect(status().isBadRequest());
        assertThat(users.findAll().getFirst().getEmail()).isEqualTo("owner@example.com");assertThat(jdbc.queryForObject("SELECT consumed_at IS NULL FROM recent_auth_proofs",Boolean.class)).isTrue();
        mvc.perform(post("/api/me/email-verification-challenges/"+challenge+"/confirm").header("Authorization",owner.access).contentType("application/json").content(json.writeValueAsString(Map.of("code",actual,"recentAuthProof",proof)))).andExpect(status().isOk());
    }
    @Test void competingEmailConfirmations_shouldHaveOneWinnerAndPreserveLosingOwnersState() throws Exception {
        Pair owner=register("Owner"),other=register("Other");String first=createChallenge(owner,"claim@example.com");String second=createChallenge(other,"claim@example.com");
        String firstCode=code(first),secondCode=code(second),firstProof=accountProof(owner),secondProof=accountProof(other);java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        try(java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            java.util.concurrent.Future<Integer> a=pool.submit(()->{start.await();return confirmationStatus(owner,first,firstCode,firstProof);}),b=pool.submit(()->{start.await();return confirmationStatus(other,second,secondCode,secondProof);});start.countDown();
            assertThat(java.util.List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email='claim@example.com'",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recent_auth_proofs WHERE consumed_at IS NOT NULL",Integer.class)).isOne();
        assertThat(users.count()).isEqualTo(2);assertThat(sessions.count()).isEqualTo(2);
    }
    @Test void repeatedImmediateUserChallenges_shouldNotHaveOwnerOrDestinationLimits() throws Exception {
        Pair owner=register("Unlimited");String latest=null;
        for(int i=0;i<8;i++)latest=createChallenge(owner,"unlimited-next@example.com");
        assertThat(users.findAll().getFirst().getEmail()).isEqualTo("unlimited@example.com");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification_challenges WHERE active",Integer.class)).isOne();
        assertThat(confirmationStatus(owner,latest,code(latest),accountProof(owner))).isEqualTo(200);
        assertThat(users.findAll().getFirst().getEmail()).isEqualTo("unlimited-next@example.com");
    }
    private String createChallenge(Pair owner,String destination) throws Exception {
        return json.readTree(mvc.perform(post("/api/me/email-verification-challenges").header("Authorization",owner.access).contentType("application/json").content(json.writeValueAsString(Map.of("purpose","EMAIL_CHANGE","destination",destination)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
    }
    private String accountProof(Pair owner) throws Exception {
        return json.readTree(mvc.perform(post("/api/me/reauthentications").header("Authorization",owner.access).contentType("application/json").content("{\"operation\":\"EMAIL_CHANGE\",\"password\":\"Password 123\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("recentAuthProof").asString();
    }
    private int confirmationStatus(Pair owner,String challenge,String code,String proof) throws Exception {
        return mvc.perform(post("/api/me/email-verification-challenges/"+challenge+"/confirm").header("Authorization",owner.access).contentType("application/json").content(json.writeValueAsString(Map.of("code",code,"recentAuthProof",proof)))).andReturn().getResponse().getStatus();
    }
    private String code(String challenge){byte[] payload=jdbc.queryForObject("SELECT encrypted_payload FROM verification_mail_outbox WHERE challenge_id=?",byte[].class,java.util.UUID.fromString(challenge));return (String)json.readValue(identityProofs.decrypt(payload,challenge),Map.class).get("code");}
}
