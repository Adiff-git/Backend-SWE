package com.example.demo.auth.service;

import com.example.demo.auth.dto.response.PendingRegistrationResponse;
import com.example.demo.auth.integration.AuthPostgresTestSupport;
import com.example.demo.config.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@Import(IdentityCleanupIT.TimeConfiguration.class)
class IdentityCleanupIT extends AuthPostgresTestSupport {
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary MutableClock controlledClock(){return new MutableClock();}
    }
    @Autowired MutableClock clock;
    @Autowired PendingRegistrationService registrations;
    @Autowired IdentityCleanupService cleanupService;
    @Test void cleanup_shouldRespectExact24HourBoundaryAndDeleteForeignKeyChildrenFirst(){
        clock.set(Instant.now());PendingRegistrationResponse pending=registrations.initiate("expire@example.com","Initial 123");
        clock.set(pending.getExpiresAt().plusSeconds(86400).minusNanos(1000));cleanupService.cleanup();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_registrations",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT encrypted_payload IS NULL FROM verification_mail_outbox",Boolean.class)).isTrue();
        clock.set(pending.getExpiresAt().plusSeconds(86400).plusNanos(1000));cleanupService.cleanup();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_registrations",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification_challenges",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_mail_outbox",Integer.class)).isZero();
        
    }
    @Test void sessionCleanup_shouldDeleteBoundProofStateBeforeItsReferencedSession(){
        clock.set(Instant.now());
        com.example.demo.auth.dto.response.PendingRegistrationResponse pending=registrations.initiate("sessioncleanup@example.com","Initial 123");
        byte[] encrypted=jdbc.queryForObject("SELECT encrypted_payload FROM verification_mail_outbox",byte[].class);
        String code=(String)json.readValue(identityProofs.decrypt(encrypted,pending.getChallengeId().toString()),java.util.Map.class).get("code");
        registrations.confirm(pending.getRegistrationCredential(),pending.getChallengeId(),code,"Password 123");
        Long owner=users.findAll().getFirst().getId();java.util.UUID sid=sessions.findAll().getFirst().getId();
        recent.password(owner,sid,com.example.demo.auth.entity.RecentAuthProof.Operation.CONNECT_GOOGLE,"Password 123");
        jdbc.update("UPDATE authentication_sessions SET created_at=created_at-INTERVAL '8 days',expires_at=expires_at-INTERVAL '8 days' WHERE id=?",sid);
        commands.cleanupOwner(owner,clock.instant());
        assertThat(sessions.count()).isZero();assertThat(credentials.count()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recent_auth_proofs",Integer.class)).isZero();
    }
    @Autowired RecentAuthenticationService recent;
    @Autowired AuthTransactionService commands;
    @Test void proofCleanup_shouldRemoveDependentExpiredAttemptAtProofDeadlineWithoutDeletingActiveSession(){
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        com.example.demo.auth.dto.response.PendingRegistrationResponse pending=registrations.initiate("proofcleanup@example.com","Initial 123");
        byte[] encrypted=jdbc.queryForObject("SELECT encrypted_payload FROM verification_mail_outbox",byte[].class);String code=(String)json.readValue(identityProofs.decrypt(encrypted,pending.getChallengeId().toString()),java.util.Map.class).get("code");
        registrations.confirm(pending.getRegistrationCredential(),pending.getChallengeId(),code,"Password 123");Long owner=users.findAll().getFirst().getId();java.util.UUID sid=sessions.findAll().getFirst().getId();
        com.example.demo.auth.dto.response.RecentAuthProofResponse proof=recent.password(owner,sid,com.example.demo.auth.entity.RecentAuthProof.Operation.CONNECT_GOOGLE,"Password 123");
        clock.set(clock.instant().plusSeconds(240));
        googleAttempts.start(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(identityProofs.hash("v".repeat(43))),"S256",null,com.example.demo.auth.entity.OAuthLoginAttempt.Purpose.LINK,com.example.demo.auth.entity.OAuthLoginAttempt.Operation.CONNECT_GOOGLE,owner,sid,proof.getRecentAuthProof());
        clock.set(proof.getExpiresAt().plusSeconds(86400));cleanupService.cleanup();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recent_auth_proofs",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oauth_login_attempts",Integer.class)).isZero();assertThat(sessions.count()).isOne();
    }
    @Autowired GoogleAttemptService googleAttempts;
    @Test void purgeOverdueMail_shouldEraseAtExpiryBeforeAnyDelivery(){
        clock.set(Instant.now());PendingRegistrationResponse pending=registrations.initiate("startup@example.com","Initial 123");
        clock.set(pending.getExpiresAt().plusSeconds(901));assertThat(cleanupService.purgeOverdueMail()).isTrue();
        assertThat(jdbc.queryForObject("SELECT encrypted_payload IS NULL AND nonce IS NULL FROM verification_mail_outbox",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT state FROM verification_mail_outbox",String.class)).isEqualTo("EXPIRED");
        
    }
}
