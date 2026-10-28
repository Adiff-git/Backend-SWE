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
import static org.assertj.core.api.Assertions.*;
@DataJpaTest @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class AuthSessionRepositoryIT extends PostgresTestSupport {
    @Autowired AuthenticationSessionRepository sessions;
    @Autowired UserRepository users;
    @Autowired jakarta.persistence.EntityManager em;
    @Test void execute_shouldPreserveFixedExpiryAndLazyOwner() {
        Long owner=users.saveAndFlush(UserTestData.user("Owner")).getId();
        var s=sessions.saveAndFlush(AuthTestData.session(owner,Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));
        em.clear();
        var loaded=sessions.findById(s.getId()).orElseThrow();
        assertThat(org.hibernate.Hibernate.isInitialized(loaded.getUser())).isFalse();
        assertThat(sessions.findByUserIdOrderById(owner)).hasSize(1);
        assertThat(loaded.getExpiresAt()).isEqualTo(loaded.getCreatedAt().plusSeconds(604800));
    }
    @Test void execute_shouldVerifyDatabaseRejectsSlidingExpiry() {
        Long owner=users.saveAndFlush(UserTestData.user("Owner")).getId();
        var s=AuthTestData.session(owner,Instant.now());
        s.setExpiresAt(s.getExpiresAt().plusSeconds(1));
        assertThatThrownBy(()->sessions.saveAndFlush(s)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void execute_shouldVerifyDatabaseRejectsRevocationWithoutReason() {
        Long owner=users.saveAndFlush(UserTestData.user("Owner")).getId();
        var s=AuthTestData.session(owner,Instant.now());
        s.setRevokedAt(Instant.now());
        assertThatThrownBy(()->sessions.saveAndFlush(s)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
