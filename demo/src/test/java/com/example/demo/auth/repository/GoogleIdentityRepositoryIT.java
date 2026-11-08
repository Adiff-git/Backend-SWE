package com.example.demo.auth.repository;

import com.example.demo.auth.entity.GoogleIdentity;
import com.example.demo.user.integration.PostgresTestSupport;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.testdata.UserTestData;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class GoogleIdentityRepositoryIT extends PostgresTestSupport {
    @Autowired GoogleIdentityRepository identities;
    @Autowired UserRepository users;
    @Autowired jakarta.persistence.EntityManager entities;
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void association_shouldEnforceBothUniqueDirections(boolean sameOwner){
        Long owner=owner("Owner"),other=owner("Other");identities.saveAndFlush(identity(owner,"subject"));
        assertThatThrownBy(()->identities.saveAndFlush(identity(sameOwner?owner:other,sameOwner?"different-subject":"subject"))).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void association_shouldKeepOpaqueSubjectCaseAndPermitMissingProviderEmail(){
        identities.saveAndFlush(identity(owner("Owner"),"MixedCase"));identities.saveAndFlush(identity(owner("Other"),"mixedcase"));
        assertThat(identities.count()).isEqualTo(2);assertThat(identities.findByProviderAndIssuerAndSubject("GOOGLE","https://accounts.google.com","MixedCase")).isPresent();
    }
    @Test void association_shouldNeverPersistReassignmentOrLinkedTimeChanges(){
        Long owner=owner("Owner"),other=owner("Other");GoogleIdentity stored=identities.saveAndFlush(identity(owner,"original"));Long id=stored.getId();Instant linked=stored.getLinkedAt();
        stored.setUserId(other);stored.setSubject("replacement");stored.setLinkedAt(linked.plusSeconds(60));identities.flush();entities.clear();
        GoogleIdentity loaded=identities.findById(id).orElseThrow();assertThat(loaded.getUserId()).isEqualTo(owner);assertThat(loaded.getSubject()).isEqualTo("original");assertThat(loaded.getLinkedAt()).isEqualTo(linked);
    }
    @Test void association_shouldRejectNoncanonicalIssuer(){
        GoogleIdentity row=identity(owner("Owner"),"subject");row.setIssuer("http://accounts.google.com");assertThatThrownBy(()->identities.saveAndFlush(row)).isInstanceOf(DataIntegrityViolationException.class);
    }
    private Long owner(String name){return users.saveAndFlush(UserTestData.user(name)).getId();}
    private GoogleIdentity identity(Long owner,String subject){GoogleIdentity row=new GoogleIdentity();row.setUserId(owner);row.setProvider("GOOGLE");row.setIssuer("https://accounts.google.com");row.setSubject(subject);row.setLinkedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));return row;}
}
