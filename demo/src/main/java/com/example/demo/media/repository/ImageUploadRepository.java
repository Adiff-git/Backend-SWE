package com.example.demo.media.repository;

import java.util.UUID;

import com.example.demo.media.entity.ImageUpload;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageUploadRepository extends JpaRepository<ImageUpload, UUID> {
    Optional<ImageUpload> findByIdAndOwnerUserId(UUID id, Long ownerUserId);
}
