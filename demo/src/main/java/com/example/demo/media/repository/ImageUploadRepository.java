package com.example.demo.media.repository;

import java.util.UUID;

import com.example.demo.media.entity.ImageUpload;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageUploadRepository extends JpaRepository<ImageUpload, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("SELECT i FROM ImageUpload i WHERE i.id=:id")
    Optional<ImageUpload> findForReviewAttachment(UUID id);
    Optional<ImageUpload> findByIdAndOwnerUserId(UUID id, Long ownerUserId);
}
