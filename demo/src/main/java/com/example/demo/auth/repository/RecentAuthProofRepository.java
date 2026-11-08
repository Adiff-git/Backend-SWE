package com.example.demo.auth.repository;

import com.example.demo.auth.entity.RecentAuthProof;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RecentAuthProofRepository extends JpaRepository<RecentAuthProof, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from RecentAuthProof e where e.id = :id")
    Optional<RecentAuthProof> findForUpdate(UUID id);
    Optional<RecentAuthProof> findBySecretHash(byte[] hash);
}
