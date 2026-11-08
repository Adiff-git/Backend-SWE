package com.example.demo.auth.service;

import com.example.demo.auth.dto.response.PendingRegistrationResponse;
import com.example.demo.auth.integration.AuthPostgresTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@org.springframework.context.annotation.Import(IdentityCleanupIT.TimeConfiguration.class)
class VerificationMailDeliveryIT extends AuthPostgresTestSupport {
    @Autowired com.example.demo.config.MutableClock clock;
    @org.junit.jupiter.api.BeforeEach void resetClock(){clock.set(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));}
    @Autowired PendingRegistrationService registrations;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean VerificationMailService mail;
    @MockitoBean JavaMailSender sender;
    @Test void dispatch_shouldSendOnlyMailboxProofAndErasePayloadAfterSuccess(){
        PendingRegistrationResponse pending=registrations.initiate("delivery@example.com","Never email 123");
        byte[] encrypted=jdbc.queryForObject("SELECT encrypted_payload FROM verification_mail_outbox",byte[].class);
        String code=(String)json.readValue(identityProofs.decrypt(encrypted,pending.getChallengeId().toString()),Map.class).get("code");
        mail.dispatch();ArgumentCaptor<SimpleMailMessage> message=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(message.capture());
        assertThat(message.getValue().getText()).contains(code,pending.getChallengeId().toString()).doesNotContain("Never email 123","Bearer",pending.getRegistrationCredential());
        assertThat(jdbc.queryForObject("SELECT state FROM verification_mail_outbox",String.class)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT encrypted_payload IS NULL AND nonce IS NULL FROM verification_mail_outbox",Boolean.class)).isTrue();
        assertThat(users.count()).isZero();
    }
    @Test void dispatch_shouldBoundRetriesOnDeliveryFailure(){
        registrations.initiate("failure@example.com","Initial 123");doThrow(new MailSendException("Controlled failure")).when(sender).send(any(SimpleMailMessage.class));
        long[] delays={5,30,120};
        for(int attempt=0;attempt<4;attempt++){
            java.time.Instant before=clock.instant();mail.dispatch();
            if(attempt<3){
                java.time.Instant next=jdbc.queryForObject("SELECT next_attempt_at FROM verification_mail_outbox",java.sql.Timestamp.class).toInstant();assertThat(next).isEqualTo(before.plusSeconds(delays[attempt]));
                clock.set(next.minusNanos(1000));mail.dispatch();assertThat(jdbc.queryForObject("SELECT attempt_count FROM verification_mail_outbox",Integer.class)).isEqualTo(attempt+1);clock.set(next);
            }
        }
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM verification_mail_outbox",Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT state FROM verification_mail_outbox",String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT encrypted_payload IS NULL FROM verification_mail_outbox",Boolean.class)).isTrue();
        assertThat(users.count()).isZero();
    }
    @Test void enqueueFailure_shouldRollbackPendingChallengeAndOutbox(){
        org.mockito.Mockito.doAnswer(call->{call.callRealMethod();throw new IllegalStateException("Controlled outbox failure");}).when(mail).enqueue(any(),org.mockito.ArgumentMatchers.anyString());
        org.assertj.core.api.Assertions.assertThatThrownBy(()->registrations.initiate("rollback@example.com","Initial 123")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_registrations",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification_challenges",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_mail_outbox",Integer.class)).isZero();
        
    }
    @Test void expiredLease_shouldRecoverAtExactDeadline(){
        registrations.initiate("lease@example.com","Initial 123");java.time.Instant lease=clock.instant().plusSeconds(30);
        jdbc.update("UPDATE verification_mail_outbox SET state='LEASED',attempt_count=1,lease_until=?",java.sql.Timestamp.from(lease));
        clock.set(lease.minusNanos(1000));mail.dispatch();org.mockito.Mockito.verify(sender,org.mockito.Mockito.never()).send(any(SimpleMailMessage.class));
        clock.set(lease);mail.dispatch();verify(sender).send(any(SimpleMailMessage.class));
        assertThat(jdbc.queryForObject("SELECT state FROM verification_mail_outbox",String.class)).isEqualTo("SENT");assertThat(jdbc.queryForObject("SELECT attempt_count FROM verification_mail_outbox",Integer.class)).isEqualTo(2);
        
    }
    @Test void supersededMailAndRepeatedDispatch_shouldDeliverOnlyCurrentGenerationOnce(){
        PendingRegistrationResponse first=registrations.initiate("superseded@example.com","Initial 123");
        PendingRegistrationResponse next=registrations.resend(first.getRegistrationCredential(),first.getChallengeId());mail.dispatch();mail.dispatch();
        ArgumentCaptor<SimpleMailMessage> message=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(message.capture());assertThat(message.getValue().getText()).contains(next.getChallengeId().toString()).doesNotContain(first.getChallengeId().toString());
        assertThat(jdbc.queryForObject("SELECT state FROM verification_mail_outbox WHERE challenge_id=?",String.class,first.getChallengeId())).isEqualTo("EXPIRED");
        
    }
    @Test void overduePayload_shouldBePurgedBeforeAnyDispatchOnRecovery(){
        registrations.initiate("overdue@example.com","Initial 123");clock.set(clock.instant().plusSeconds(1501));mail.dispatch();org.mockito.Mockito.verify(sender,org.mockito.Mockito.never()).send(any(SimpleMailMessage.class));
        assertThat(jdbc.queryForObject("SELECT encrypted_payload IS NULL AND nonce IS NULL FROM verification_mail_outbox",Boolean.class)).isTrue();assertThat(jdbc.queryForObject("SELECT state FROM verification_mail_outbox",String.class)).isEqualTo("EXPIRED");
    }
}
