package com.example.demo.auth.repository;
import com.example.demo.auth.entity.AuthenticationSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
public interface AuthenticationSessionRepository extends JpaRepository<AuthenticationSession,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from AuthenticationSession s where s.id=:id") Optional<AuthenticationSession> findForUpdate(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) List<AuthenticationSession> findByUserIdOrderById(Long userId);
    @Query(value="SELECT DISTINCT user_id FROM authentication_sessions WHERE expires_at<:deadline ORDER BY user_id LIMIT :limit",nativeQuery=true) List<Long> findExpiredOwnerIds(Instant deadline,int limit);
    @Lock(LockModeType.PESSIMISTIC_WRITE) List<AuthenticationSession> findTop100ByUserIdAndExpiresAtLessThanOrderById(Long userId,Instant deadline);
}
