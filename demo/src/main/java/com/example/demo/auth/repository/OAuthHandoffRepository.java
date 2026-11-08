package com.example.demo.auth.repository;

import com.example.demo.auth.entity.OAuthHandoff;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OAuthHandoffRepository extends JpaRepository<OAuthHandoff, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OAuthHandoff e where e.id = :id")
    Optional<OAuthHandoff> findForUpdate(UUID id);
    Optional<OAuthHandoff> findByCodeHash(byte[] hash);
}
