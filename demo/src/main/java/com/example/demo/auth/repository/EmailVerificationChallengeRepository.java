package com.example.demo.auth.repository;

import com.example.demo.auth.entity.EmailVerificationChallenge;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EmailVerificationChallengeRepository extends JpaRepository<EmailVerificationChallenge,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from EmailVerificationChallenge c where c.id=:id")
    Optional<EmailVerificationChallenge> findForUpdate(UUID id);
    Optional<EmailVerificationChallenge> findByUserIdAndPurposeAndActiveTrue(Long userId,EmailVerificationChallenge.Purpose purpose);
}
