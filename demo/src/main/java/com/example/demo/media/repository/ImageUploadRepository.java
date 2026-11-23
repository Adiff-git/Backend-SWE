package com.example.demo.media.repository;

import java.util.UUID;

import com.example.demo.media.entity.ImageUpload;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageUploadRepository extends JpaRepository<ImageUpload, UUID> {
    /** Scalar snapshot avoids returning an old managed upload from the preparation phase. */
    @org.springframework.data.jpa.repository.Query(value = "SELECT id,owner_user_id,version,final_key,content_type,status FROM image_uploads WHERE id=:id FOR SHARE", nativeQuery = true)
    java.util.List<Object[]> lockProductAttachmentSnapshot(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("SELECT i FROM ImageUpload i WHERE i.id=:id")
    Optional<ImageUpload> findForReviewAttachment(UUID id);
    Optional<ImageUpload> findByIdAndOwnerUserId(UUID id, Long ownerUserId);
}
