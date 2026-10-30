package com.example.demo.auth.repository;
import com.example.demo.auth.testdata.AuthTestData;
import com.example.demo.user.testdata.UserTestData;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.integration.PostgresTestSupport;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@DataJpaTest @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class RefreshCredentialRepositoryIT extends PostgresTestSupport {
    @Autowired AuthenticationSessionRepository sessions;
    @Autowired RefreshCredentialRepository credentials;
    @Autowired UserRepository users;
    @Autowired jakarta.persistence.EntityManager em;
    java.util.UUID session() {
        Long owner=users.saveAndFlush(UserTestData.user("Owner")).getId();
        return sessions.saveAndFlush(AuthTestData.session(owner,Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))).getId();
    }
    @Test void execute_shouldVerifyDatabaseAllowsOnlyOneUnconsumedCredential() {
        var sid=session();
        credentials.saveAndFlush(AuthTestData.credential(sid,new byte[32],Instant.now()));
        byte[] other=new byte[32];
        other[0]=1;
        assertThatThrownBy(()->credentials.saveAndFlush(AuthTestData.credential(sid,other,Instant.now()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void execute_shouldVerifyDatabaseRejectsNonDigestAndDuplicateDigest() {
        var sid=session();
        assertThatThrownBy(()->credentials.saveAndFlush(AuthTestData.credential(sid,new byte[31],Instant.now()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void execute_shouldRetainConsumedEvidenceAndLoadsSessionLazily() {
        var sid=session();
        var old=credentials.saveAndFlush(AuthTestData.credential(sid,new byte[32],Instant.now()));
        old.setConsumedAt(Instant.now());
        credentials.flush();
        byte[] next=new byte[32];
        next[0]=1;
        credentials.saveAndFlush(AuthTestData.credential(sid,next,Instant.now()));
        em.clear();
        var found=credentials.findByTokenHash(new byte[32]).orElseThrow();
        assertThat(found.getConsumedAt()).isNotNull();
        assertThat(org.hibernate.Hibernate.isInitialized(found.getSession())).isFalse();
        assertThat(credentials.count()).isEqualTo(2);
    }
    @Test void save_shouldRejectDuplicateDigestEvenAfterConsumption() {
        var sid=session();var old=credentials.saveAndFlush(AuthTestData.credential(sid,new byte[32],Instant.now()));old.setConsumedAt(Instant.now());credentials.flush();
        assertThatThrownBy(()->credentials.saveAndFlush(AuthTestData.credential(sid,new byte[32],Instant.now()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
