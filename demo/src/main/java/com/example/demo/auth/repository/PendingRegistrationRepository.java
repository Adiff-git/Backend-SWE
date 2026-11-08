package com.example.demo.auth.repository;

import com.example.demo.auth.entity.PendingRegistration;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration,UUID> {
    Optional<PendingRegistration> findByCredentialHash(byte[] hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from PendingRegistration p where p.id=:id")
    Optional<PendingRegistration> findForUpdate(UUID id);
}
