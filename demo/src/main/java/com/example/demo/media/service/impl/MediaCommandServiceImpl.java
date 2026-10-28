package com.example.demo.media.service.impl;

import com.example.demo.common.identity.IdentityUnavailableException;
import com.example.demo.media.converter.MediaConverter;
import com.example.demo.media.dto.request.CreateImageUploadRequest;
import com.example.demo.media.dto.response.ImageUploadResponse;
import com.example.demo.media.entity.ImageUpload;
import com.example.demo.media.enums.ImageUploadStatus;
import com.example.demo.media.exception.ImageUploadNotFoundException;
import com.example.demo.media.exception.UploadStateConflictException;
import com.example.demo.media.repository.ImageUploadRepository;
import com.example.demo.media.service.ImageCompletionOutcome;
import com.example.demo.media.service.MediaCommandService;
import com.example.demo.media.validator.ImageContentValidator;
import com.example.demo.media.validator.ImageUploadValidator;
import com.example.demo.storage.config.S3StorageProperties;
import com.example.demo.storage.dto.response.StoredObjectContent;
import com.example.demo.storage.dto.response.StoredObjectMetadata;
import com.example.demo.storage.dto.response.PreparedUpload;
import com.example.demo.storage.exception.StoredObjectChangedException;
import com.example.demo.storage.exception.StorageUnavailableException;
import com.example.demo.storage.service.ObjectStorageService;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class MediaCommandServiceImpl implements MediaCommandService {
    private static final Logger LOG = LoggerFactory.getLogger(MediaCommandServiceImpl.class);
    private final ImageUploadRepository repository;
    private final ObjectStorageService storage;
    private final ImageContentValidator contentValidator;
    private final ImageUploadValidator validator;
    private final MediaConverter converter;
    private final S3StorageProperties properties;
    private final Clock clock;

    @Override @Transactional
    public ImageUploadResponse create(Long actorId, CreateImageUploadRequest request) {
        requireActor(actorId);
        validator.validateType(request.getContentType());
        Instant created = clock.instant();
        String stagingKey = "staging/users/" + actorId + "/" + UUID.randomUUID();
        PreparedUpload signed = storage.prepareUpload(stagingKey, request.getContentType(), created.plus(properties.getUploadUrlLifetime()));
        ImageUpload upload = ImageUpload.pending(actorId, stagingKey,
                request.getContentType(), signed.getExpiresAt().minus(properties.getUploadUrlLifetime()), signed.getExpiresAt());
        repository.saveAndFlush(upload);
        return converter.toUpload(upload, signed.getUrl());
    }

    @Override @Transactional
    public ImageCompletionOutcome complete(Long actorId, Long imageId) {
        requireActor(actorId);
        validator.validateId(imageId);
        ImageUpload image = repository.findByIdAndOwnerUserId(imageId, actorId).orElseThrow(ImageUploadNotFoundException::new);
        if (image.getStatus() == ImageUploadStatus.COMPLETED) return ImageCompletionOutcome.completed(image);
        if (image.getStatus() == ImageUploadStatus.EXPIRED) return ImageCompletionOutcome.expired(image);
        if (!clock.instant().isBefore(image.getExpiresAt())) return expire(image);
        StoredObjectMetadata observed = storage.inspect(image.getStagingKey()).orElseThrow(UploadStateConflictException::new);
        String target = "images/users/" + actorId + "/" + UUID.randomUUID();
        try (StoredObjectContent content = storage.readIfMatch(image.getStagingKey(), observed.getEtag())) {
            if (!observed.getEtag().equals(content.getMetadata().getEtag())) throw new StoredObjectChangedException();
            contentValidator.validate(content, image.getContentType());
            storage.copyIfMatch(image.getStagingKey(), content.getMetadata().getEtag(), target, image.getContentType());
        } catch (IOException failure) { throw new StorageUnavailableException(); }
        if (!clock.instant().isBefore(image.getExpiresAt())) return expire(image);
        image.complete(target);
        repository.flush();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { storage.deleteStaging(image.getStagingKey()); }
                catch (RuntimeException failure) { LOG.warn("Staging cleanup failed after image completion; manual reconciliation required"); }
            }
        });
        return ImageCompletionOutcome.completed(image);
    }

    private ImageCompletionOutcome expire(ImageUpload image) {
        image.expire();
        repository.flush();
        return ImageCompletionOutcome.expired(image);
    }

    private static void requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) throw new IdentityUnavailableException();
    }
}
