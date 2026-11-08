package com.example.demo.auth.repository;

import com.example.demo.auth.entity.VerificationMailOutbox;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VerificationMailOutboxRepository extends JpaRepository<VerificationMailOutbox,Long> {
    @Query(value="SELECT * FROM verification_mail_outbox WHERE challenge_id=:challenge ORDER BY id FOR UPDATE",nativeQuery=true)
    List<VerificationMailOutbox> findForUpdateByChallengeId(UUID challenge);
    @Query(value="SELECT * FROM verification_mail_outbox WHERE (state='PENDING' AND next_attempt_at<=:now OR state='LEASED' AND lease_until<=:now) ORDER BY id LIMIT 20 FOR UPDATE SKIP LOCKED",nativeQuery=true)
    List<VerificationMailOutbox> findDispatchBatch(java.time.Instant now);
}
