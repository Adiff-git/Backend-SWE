package com.example.demo.media.service.impl;

import java.util.UUID;

import com.example.demo.common.identity.IdentityUnavailableException;
import com.example.demo.media.converter.MediaConverter;
import com.example.demo.media.dto.request.CreateImageUploadRequest;
import com.example.demo.media.dto.response.ImageAccessResponse;
import com.example.demo.media.dto.response.ImageUploadResponse;
import com.example.demo.media.entity.ImageUpload;
import com.example.demo.media.enums.ImageUploadStatus;
import com.example.demo.media.exception.ImageUploadNotFoundException;
import com.example.demo.media.exception.UploadExpiredException;
import com.example.demo.media.exception.UploadStateConflictException;
import com.example.demo.media.repository.ImageUploadRepository;
import com.example.demo.media.service.ImageCompletionOutcome;
import com.example.demo.media.service.MediaCommandService;
import com.example.demo.media.service.MediaService;
import com.example.demo.media.validator.ImageUploadValidator;
import com.example.demo.storage.config.S3StorageProperties;
import com.example.demo.storage.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {
    private final MediaCommandService commands;
    private final ImageUploadRepository repository;
    private final ObjectStorageService storage;
    private final MediaConverter converter;
    private final ImageUploadValidator validator;
    private final S3StorageProperties properties;

    @Override public ImageUploadResponse createImageUpload(Long actorId, CreateImageUploadRequest request) {
        requireEntryBoundary();
        return commands.create(actorId, request);
    }
    @Override public ImageAccessResponse completeImageUpload(Long actorId, UUID imageId) {
        requireEntryBoundary();
        ImageCompletionOutcome outcome = commands.complete(actorId, imageId);
        if (outcome.isExpired()) throw new UploadExpiredException();
        return access(outcome.getId(), outcome.getFinalKey());
    }
    @Override @Transactional(readOnly = true)
    public ImageAccessResponse getImageDownload(Long actorId, UUID imageId) { return ownedAccess(actorId, imageId); }
    @Override @Transactional(readOnly = true)
    public ImageAccessResponse validateImageForAttachment(Long actorId, UUID imageId) { return ownedAccess(actorId, imageId); }
    @Override @Transactional(readOnly = true)
    public ImageAccessResponse getAttachedImage(UUID imageId) {
        validator.validateId(imageId);
        return completedAccess(repository.findById(imageId).orElseThrow(ImageUploadNotFoundException::new));
    }
    private ImageAccessResponse ownedAccess(Long actorId, UUID imageId) {
        if (actorId == null || actorId <= 0) throw new IdentityUnavailableException();
        validator.validateId(imageId);
        return completedAccess(repository.findByIdAndOwnerUserId(imageId, actorId).orElseThrow(ImageUploadNotFoundException::new));
    }
    private ImageAccessResponse completedAccess(ImageUpload image) {
        if (image.getStatus() != ImageUploadStatus.COMPLETED) throw new UploadStateConflictException();
        return access(image.getId(), image.getFinalKey());
    }
    private ImageAccessResponse access(UUID id, String key) {
        storage.verifyFinalExists(key);
        return converter.toAccess(id, storage.prepareDownload(key, properties.getDownloadUrlLifetime()));
    }
    private void requireEntryBoundary() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Media completion facade must not join an ambient transaction");
    }
}
