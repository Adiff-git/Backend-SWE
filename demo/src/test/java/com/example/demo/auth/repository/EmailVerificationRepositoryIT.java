package com.example.demo.auth.repository;

import com.example.demo.auth.entity.EmailVerificationChallenge;
import com.example.demo.auth.entity.PendingRegistration;
import com.example.demo.user.integration.PostgresTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class EmailVerificationRepositoryIT extends PostgresTestSupport {
    @Autowired EmailVerificationChallengeRepository challenges;
    @Autowired PendingRegistrationRepository pending;
    @Test void insert_shouldRejectMissingOwner(){assertThatThrownBy(()->challenges.saveAndFlush(challenge())).isInstanceOf(DataIntegrityViolationException.class);}
    @Test void insert_shouldPermitOnlyOneActiveChallengePerPendingOwner(){
        PendingRegistration owner=owner();EmailVerificationChallenge first=challenge();first.setPendingRegistrationId(owner.getId());challenges.saveAndFlush(first);
        EmailVerificationChallenge second=challenge();second.setPendingRegistrationId(owner.getId());
        assertThatThrownBy(()->challenges.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void insert_shouldRejectPendingEmailChange(){
        PendingRegistration owner=owner();EmailVerificationChallenge row=challenge();row.setPendingRegistrationId(owner.getId());row.setPurpose(EmailVerificationChallenge.Purpose.EMAIL_CHANGE);
        assertThatThrownBy(()->challenges.saveAndFlush(row)).isInstanceOf(DataIntegrityViolationException.class);
    }
    private PendingRegistration owner(){
        PendingRegistration row=new PendingRegistration();row.setId(UUID.randomUUID());row.setDestination("same@example.test");row.setCredentialHash(new byte[32]);row.setGeneration(1);row.setState(PendingRegistration.State.ACTIVE);row.setCreatedAt(Instant.now());row.setExpiresAt(row.getCreatedAt().plusSeconds(600));return pending.saveAndFlush(row);
    }
    private EmailVerificationChallenge challenge(){
        EmailVerificationChallenge row=new EmailVerificationChallenge();row.setId(UUID.randomUUID());row.setGeneration(1);row.setDestination("same@example.test");row.setPurpose(EmailVerificationChallenge.Purpose.REGISTRATION);
        row.setCodeMac(new byte[32]);row.setKeyId("v1");row.setActive(true);row.setCreatedAt(Instant.now());row.setExpiresAt(row.getCreatedAt().plusSeconds(600));row.setDeliveryStatus(EmailVerificationChallenge.DeliveryStatus.QUEUED);return row;
    }
}
