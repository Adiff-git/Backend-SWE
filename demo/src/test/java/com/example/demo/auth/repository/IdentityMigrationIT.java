package com.example.demo.auth.repository;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityMigrationIT {
    @Test void migrate_shouldPreserveLegacyUsersAndEnforceIdentityOwnership() {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17-alpine")) {
            database.start();
            DriverManagerDataSource source = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            Flyway.configure().dataSource(source).target("12").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(source);
            Long legacy = user(jdbc, "Legacy", "legacy@example.test", "existing-hash");
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForObject("SELECT password FROM users WHERE id=?", String.class, legacy)).isEqualTo("existing-hash");
            assertThat(jdbc.queryForObject("SELECT email_verified_at FROM users WHERE id=?", java.time.OffsetDateTime.class, legacy)).isNull();
            Long social = user(jdbc, "Social", "social@example.test", null);
            assertThatThrownBy(() -> user(jdbc, "legacy", "other@example.test", "encoded"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> user(jdbc, "Other", "LEGACY@example.test", "encoded"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("INSERT INTO google_identities(user_id,provider,issuer,subject,linked_at) VALUES (?,'GOOGLE','https://accounts.google.com','subject-one',NOW())", legacy);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO google_identities(user_id,provider,issuer,subject,linked_at) VALUES (?,'GOOGLE','https://accounts.google.com','subject-one',NOW())", social))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO google_identities(user_id,provider,issuer,subject,linked_at) VALUES (?,'GOOGLE','https://accounts.google.com','subject-two',NOW())", legacy))
                    .isInstanceOf(DataIntegrityViolationException.class);
            UUID pending = UUID.randomUUID();
            jdbc.update("INSERT INTO pending_registrations(id,destination,credential_hash,generation,state,created_at,expires_at) VALUES (?,? ,?,1,'ACTIVE',NOW(),NOW()+INTERVAL '10 minutes')", pending, "pending@example.test", new byte[32]);
            jdbc.update("INSERT INTO pending_registrations(id,destination,credential_hash,generation,state,created_at,expires_at) VALUES (?,? ,?,1,'ACTIVE',NOW(),NOW()+INTERVAL '10 minutes')", UUID.randomUUID(), "pending@example.test", filled(1));
            assertThatThrownBy(() -> jdbc.update("INSERT INTO email_verification_challenges(id,user_id,pending_registration_id,generation,destination,purpose,code_mac,key_id,created_at,expires_at,active,delivery_status) VALUES (?,?,?,1,'pending@example.test','REGISTRATION',?,'v1',NOW(),NOW()+INTERVAL '10 minutes',TRUE,'QUEUED')", UUID.randomUUID(), legacy, pending, new byte[32]))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
    @Test void removeRateLimiting_shouldPreserveAcceptedBusinessStateAcrossV13Upgrade() {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17-alpine")) {
            database.start();
            DriverManagerDataSource source = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            Flyway.configure().dataSource(source).target("13").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(source);
            Long legacy = user(jdbc, "Retained", "retained@example.test", "existing-hash");
            UUID pending=UUID.randomUUID(), challenge=UUID.randomUUID(), reservation=UUID.randomUUID();
            jdbc.update("INSERT INTO pending_registrations(id,destination,credential_hash,generation,state,created_at,expires_at) VALUES (?,'pending@example.test',?,1,'ACTIVE',NOW(),NOW()+INTERVAL '10 minutes')",pending,new byte[32]);
            jdbc.update("INSERT INTO email_verification_challenges(id,pending_registration_id,generation,destination,purpose,code_mac,key_id,created_at,expires_at,active,delivery_status) VALUES (?,?,1,'pending@example.test','REGISTRATION',?,'v1',NOW(),NOW()+INTERVAL '10 minutes',TRUE,'QUEUED')",challenge,pending,new byte[32]);
            jdbc.update("INSERT INTO rate_limit_reservations(id,policy_family,operation_kind,state,admitted_at,request_deadline,lease_until) SELECT ?,'OTP_SEND','PENDING_INITIAL','RESERVED',t,t+INTERVAL '30 seconds',t+INTERVAL '35 seconds' FROM (SELECT clock_timestamp() t) v",reservation);
            jdbc.update("INSERT INTO verification_mail_outbox(challenge_id,rate_reservation_id,encrypted_payload,nonce,key_id,state,next_attempt_at,expires_at) VALUES (?,?,?,?,'v1','PENDING',NOW(),NOW()+INTERVAL '10 minutes')",challenge,reservation,new byte[]{1,2,3},new byte[12]);
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForObject("SELECT password FROM users WHERE id=?",String.class,legacy)).isEqualTo("existing-hash");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_registrations WHERE id=?",Integer.class,pending)).isOne();
            assertThat(jdbc.queryForObject("SELECT active FROM email_verification_challenges WHERE id=?",Boolean.class,challenge)).isTrue();
            assertThat(jdbc.queryForObject("SELECT encrypted_payload FROM verification_mail_outbox WHERE challenge_id=?",byte[].class,challenge)).containsExactly(1,2,3);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'rate_limit_%'",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND column_name IN ('rate_reservation_id','failed_attempts')",Integer.class)).isZero();
        }
    }
    private Long user(JdbcTemplate jdbc, String username, String email, String password) {
        return jdbc.queryForObject("INSERT INTO users(username,email,password,account_status,joined_at) VALUES (?,?,?,'ACTIVE',NOW()) RETURNING id", Long.class, username, email, password);
    }
    private byte[] filled(int value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
