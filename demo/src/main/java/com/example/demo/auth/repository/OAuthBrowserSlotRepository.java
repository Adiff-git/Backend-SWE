package com.example.demo.auth.repository;

import com.example.demo.auth.entity.OAuthBrowserSlot;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OAuthBrowserSlotRepository extends JpaRepository<OAuthBrowserSlot, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OAuthBrowserSlot e where e.id = :id")
    Optional<OAuthBrowserSlot> findForUpdate(UUID id);
}
