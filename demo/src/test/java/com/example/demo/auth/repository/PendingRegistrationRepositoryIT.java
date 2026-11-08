package com.example.demo.auth.repository;

import com.example.demo.auth.entity.PendingRegistration;
import com.example.demo.user.integration.PostgresTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class PendingRegistrationRepositoryIT extends PostgresTestSupport {
    @Autowired PendingRegistrationRepository repository;
    @Test void insert_shouldAllowIndependentAttemptsForSameDestinationWithoutUserOrPassword(){
        PendingRegistration first=attempt((byte)1),second=attempt((byte)2);
        repository.saveAndFlush(first);repository.saveAndFlush(second);
        assertThat(repository.findByCredentialHash(first.getCredentialHash())).get().extracting(PendingRegistration::getId).isEqualTo(first.getId());
        assertThat(repository.findAll()).hasSize(2);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"MISSING_SECRET","SHORT_SECRET","BAD_GENERATION","UNNORMALIZED_DESTINATION","NONFUTURE_EXPIRY"})
    void insert_shouldEnforcePendingStateConstraints(String invalid){
        PendingRegistration row=attempt((byte)1);
        switch(invalid){
            case "MISSING_SECRET" -> row.setCredentialHash(null);
            case "SHORT_SECRET" -> row.setCredentialHash(new byte[31]);
            case "BAD_GENERATION" -> row.setGeneration(0);
            case "UNNORMALIZED_DESTINATION" -> row.setDestination("Upper@Example.com");
            case "NONFUTURE_EXPIRY" -> row.setExpiresAt(row.getCreatedAt());
            default -> throw new AssertionError(invalid);
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(()->repository.saveAndFlush(row)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void insert_shouldRejectCredentialReuseAcrossIndependentAttempts(){
        repository.saveAndFlush(attempt((byte)1));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->repository.saveAndFlush(attempt((byte)1))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    private PendingRegistration attempt(byte credential){
        PendingRegistration row=new PendingRegistration();row.setId(UUID.randomUUID());row.setDestination("same@example.test");byte[] hash=new byte[32];hash[0]=credential;
        row.setCredentialHash(hash);row.setGeneration(1);row.setState(PendingRegistration.State.ACTIVE);row.setCreatedAt(Instant.now());row.setExpiresAt(row.getCreatedAt().plusSeconds(600));return row;
    }
}
