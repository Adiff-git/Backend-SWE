package com.example.demo.auth.repository;
import com.example.demo.auth.entity.RefreshCredential;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
public interface RefreshCredentialRepository extends JpaRepository<RefreshCredential,Long> {
    Optional<RefreshCredential> findByTokenHash(byte[] hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from RefreshCredential r where r.id=:id") Optional<RefreshCredential> findForUpdate(Long id);
    void deleteBySessionId(UUID sessionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) java.util.List<RefreshCredential> findBySessionIdOrderById(UUID sessionId);
}
