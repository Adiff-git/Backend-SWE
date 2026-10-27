package com.example.demo.media.repository;

import com.example.demo.media.entity.ImageUpload;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageUploadRepository extends JpaRepository<ImageUpload, Long> {
    Optional<ImageUpload> findByIdAndOwnerUserId(Long id, Long ownerUserId);
}
