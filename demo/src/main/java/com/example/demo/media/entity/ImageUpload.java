package com.example.demo.media.entity;

import com.example.demo.media.enums.ImageUploadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "image_uploads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageUpload {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long ownerUserId;
    @Column(nullable = false, unique = true, length = 512, updatable = false) private String stagingKey;
    @Column(unique = true, length = 512) private String finalKey;
    @Column(nullable = false, length = 100, updatable = false) private String contentType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ImageUploadStatus status;
    @Column(nullable = false, updatable = false) private Instant expiresAt;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Version @Column(nullable = false) private Long version;

    public static ImageUpload pending(Long ownerUserId, String stagingKey, String contentType,
            Instant createdAt, Instant expiresAt) {
        ImageUpload upload = new ImageUpload();
        upload.ownerUserId = ownerUserId;
        upload.stagingKey = stagingKey;
        upload.contentType = contentType;
        upload.createdAt = createdAt;
        upload.expiresAt = expiresAt;
        upload.status = ImageUploadStatus.PENDING;
        return upload;
    }

    public void complete(String finalKey) {
        if (status != ImageUploadStatus.PENDING || finalKey == null || finalKey.isBlank())
            throw new IllegalStateException("Invalid image lifecycle transition");
        this.finalKey = finalKey;
        status = ImageUploadStatus.COMPLETED;
    }

    public void expire() {
        if (status != ImageUploadStatus.PENDING) throw new IllegalStateException("Invalid image lifecycle transition");
        status = ImageUploadStatus.EXPIRED;
    }
}
