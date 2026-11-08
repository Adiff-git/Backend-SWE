package com.example.demo.auth.service;

import com.example.demo.auth.dto.response.PendingRegistrationResponse;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.integration.AuthPostgresTestSupport;
import com.example.demo.config.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

@Import(IdentityCleanupIT.TimeConfiguration.class)
class PendingRegistrationBoundaryIT extends AuthPostgresTestSupport {
    @Autowired MutableClock clock;
    @Autowired PendingRegistrationService registrations;
    @MockitoSpyBean IdentityProofService generator;
    @BeforeEach void resetClock(){clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));}
    @Test void confirmation_shouldPreserveLeadingZerosAndRejectExactlyAtTenMinuteDeadline(){
        doReturn("000123").when(generator).newCode();
        PendingRegistrationResponse before=registrations.initiate("before@example.com","Initial 123"),expired=registrations.initiate("expired@example.com","Initial 456");
        clock.set(before.getExpiresAt().minusNanos(1));assertThat(registrations.confirm(before.getRegistrationCredential(),before.getChallengeId(),"000123","Final 1234").getAccessToken()).isNotBlank();
        clock.set(expired.getExpiresAt());assertThatThrownBy(()->registrations.confirm(expired.getRegistrationCredential(),expired.getChallengeId(),"000123","Final 5678")).isInstanceOf(IdentityException.class);
        assertThat(users.count()).isOne();assertThat(sessions.count()).isOne();
    }
    @Test void resend_shouldPermitFreshGenerationImmediatelyAfterWrongCodes(){
        doReturn("000123").when(generator).newCode();PendingRegistrationResponse initial=registrations.initiate("exhausted@example.com","Initial 123");
        for(int index=0;index<5;index++)assertThatThrownBy(()->registrations.confirm(initial.getRegistrationCredential(),initial.getChallengeId(),"000124","Final 1234")).isInstanceOf(IdentityException.class);
        
        PendingRegistrationResponse next=registrations.resend(initial.getRegistrationCredential(),initial.getChallengeId());
        assertThat(next.getRegistrationCredential()).isNotEqualTo(initial.getRegistrationCredential());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_registrations",Integer.class)).isOne();
        
        assertThat(registrations.confirm(next.getRegistrationCredential(),next.getChallengeId(),"000123","Final 1234").getAccessToken()).isNotBlank();
    }
}
