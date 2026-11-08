package com.example.demo.user.repository;

import com.example.demo.user.integration.PostgresTestSupport;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class VerifiedAccountRepositoryIT extends PostgresTestSupport {
    @Autowired private UserRepository users;
    @Test void insertVerifiedAccount_shouldReturnOwnerAndIgnoreOnlyUsernameCollision() {
        Instant now=Instant.parse("2026-11-05T00:00:00Z");
        Long owner=users.insertVerifiedAccount("derived",null,"derived@example.test",now).orElseThrow();
        users.assignDefaultRole(owner);
        assertThat(users.findAuthorities(owner)).contains("ROLE_USER");
        assertThat(users.findById(owner).orElseThrow().getEmailVerifiedAt()).isEqualTo(now);
        assertThat(users.insertVerifiedAccount("DERIVED",null,"different@example.test",now)).isEmpty();
        Long other=users.insertVerifiedAccount("derived_2",null,"different@example.test",now).orElseThrow();
        assertThat(other).isNotEqualTo(owner);
    }
}
