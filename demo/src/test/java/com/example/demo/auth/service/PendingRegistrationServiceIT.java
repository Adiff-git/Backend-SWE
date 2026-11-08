package com.example.demo.auth.service;

import com.example.demo.auth.dto.response.PendingRegistrationResponse;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.user.integration.PostgresTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest @ActiveProfiles({"test","identity-test"})
class PendingRegistrationServiceIT extends PostgresTestSupport {
    @Autowired PendingRegistrationService registrations;
    @Autowired IdentityProofService proofs;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired PasswordEncoder passwords;
    @AfterEach void cleanup(){
        jdbc.update("DELETE FROM verification_mail_outbox");jdbc.update("DELETE FROM email_verification_challenges");jdbc.update("DELETE FROM pending_registrations");

        jdbc.update("DELETE FROM refresh_credentials");jdbc.update("DELETE FROM authentication_sessions");jdbc.update("DELETE FROM user_roles");jdbc.update("DELETE FROM users");
    }
    @Test void initiate_shouldPersistOnlyPendingProofAndAcceptedOutboxUntilAuthoritativeConfirmation(){
        PendingRegistrationResponse pending=registrations.initiate("  Alice@Example.test  ","Initial 123");
        assertThat(count("users")).isZero();assertThat(count("authentication_sessions")).isZero();assertThat(count("refresh_credentials")).isZero();
        assertThat(count("pending_registrations")).isOne();assertThat(count("verification_mail_outbox")).isOne();

        String code=code(pending.getChallengeId());
        assertThatThrownBy(()->registrations.confirm("z".repeat(43),pending.getChallengeId(),code,"Authoritative 456")).isInstanceOf(IdentityException.class);
        assertThat(count("users")).isZero();
        IssuedTokens tokens=registrations.confirm(pending.getRegistrationCredential(),pending.getChallengeId(),code,"Authoritative 456");
        assertThat(tokens.getAccessToken()).isNotBlank();assertThat(count("users")).isOne();assertThat(count("authentication_sessions")).isOne();
        assertThat(jdbc.queryForObject("SELECT username FROM users",String.class)).isEqualTo("alice");
        assertThat(passwords.matches("Authoritative 456",jdbc.queryForObject("SELECT password FROM users",String.class))).isTrue();
        assertThat(passwords.matches("Initial 123",jdbc.queryForObject("SELECT password FROM users",String.class))).isFalse();
        assertThat(jdbc.queryForObject("SELECT email_verified_at IS NOT NULL FROM users",Boolean.class)).isTrue();
        assertThatThrownBy(()->registrations.confirm(pending.getRegistrationCredential(),pending.getChallengeId(),code,"Authoritative 456")).isInstanceOf(IdentityException.class);
        assertThat(count("authentication_sessions")).isOne();
    }
    @Test void wrongCodes_shouldNotLockOutValidConfirmation(){
        PendingRegistrationResponse pending=registrations.initiate("wrong@example.test","Initial 123");String actual=code(pending.getChallengeId());String wrong="000000".equals(actual)?"000001":"000000";
        for(int i=0;i<12;i++)assertThatThrownBy(()->registrations.confirm(pending.getRegistrationCredential(),pending.getChallengeId(),wrong,"Authoritative 456")).isInstanceOf(IdentityException.class);
        assertThat(jdbc.queryForObject("SELECT active FROM email_verification_challenges",Boolean.class)).isTrue();
        registrations.confirm(pending.getRegistrationCredential(),pending.getChallengeId(),actual,"Authoritative 456");
        assertThat(count("users")).isOne();
    }
    @Test void independentPendingAttempts_shouldNotReserveOfficialEmailAndCollisionMustRollbackWholeCreation(){
        PendingRegistrationResponse first=registrations.initiate("collision@example.test","Initial 123");

        PendingRegistrationResponse second=registrations.initiate("collision@example.test","Other 456");
        assertThat(count("users")).isZero();assertThat(count("pending_registrations")).isEqualTo(2);
        String firstCode=code(first.getChallengeId()),secondCode=code(second.getChallengeId());
        registrations.confirm(first.getRegistrationCredential(),first.getChallengeId(),firstCode,"Final 1234");
        assertThatThrownBy(()->registrations.confirm(second.getRegistrationCredential(),second.getChallengeId(),secondCode,"Final 5678")).isInstanceOf(IdentityException.class).satisfies(e->assertThat(((IdentityException)e).getStatus()).isEqualTo(409));
        assertThat(count("users")).isOne();assertThat(count("authentication_sessions")).isOne();assertThat(count("refresh_credentials")).isOne();
    }
    @Test void resend_shouldRotateCredentialAndCodeWhileRetainingAttemptIdentity(){
        PendingRegistrationResponse first=registrations.initiate("rotate@example.test","Initial 123");String oldCode=code(first.getChallengeId());

        PendingRegistrationResponse next=registrations.resend(first.getRegistrationCredential(),first.getChallengeId());
        assertThat(next.getRegistrationCredential()).isNotEqualTo(first.getRegistrationCredential());assertThat(count("pending_registrations")).isOne();
        assertThat(jdbc.queryForObject("SELECT generation FROM pending_registrations",Integer.class)).isEqualTo(2);
        assertThatThrownBy(()->registrations.confirm(first.getRegistrationCredential(),first.getChallengeId(),oldCode,"Final 1234")).isInstanceOf(IdentityException.class);
        registrations.confirm(next.getRegistrationCredential(),next.getChallengeId(),code(next.getChallengeId()),"Final 1234");
        assertThat(count("users")).isOne();
    }
    @Test void repeatedImmediateSends_shouldHaveNoDestinationQuotaOrResendCooldown(){
        for(int i=0;i<8;i++)registrations.initiate("unlimited@example.test","Initial 123");
        PendingRegistrationResponse current=registrations.initiate("resend-unlimited@example.test","Initial 123");
        for(int i=0;i<8;i++)current=registrations.resend(current.getRegistrationCredential(),current.getChallengeId());
        assertThat(count("users")).isZero();assertThat(count("pending_registrations")).isEqualTo(9);
        registrations.confirm(current.getRegistrationCredential(),current.getChallengeId(),code(current.getChallengeId()),"Final 1234");
        assertThat(count("users")).isOne();
    }
    private int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    private String code(UUID id){
        byte[] payload=jdbc.queryForObject("SELECT encrypted_payload FROM verification_mail_outbox WHERE challenge_id=?",byte[].class,id);
        return (String)json.readValue(proofs.decrypt(payload,id.toString()),Map.class).get("code");
    }
}
