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
import com.example.demo.storage.service.ImageUrlResolver;
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
    private final ImageUrlResolver imageUrls;

    @Override @Transactional(readOnly = true)
    public com.example.demo.media.dto.response.PreparedProductImage prepareProductImage(Long actor, UUID id) {
        if (actor == null || actor <= 0) throw new IdentityUnavailableException();
        ImageUpload image = repository.findByIdAndOwnerUserId(id, actor).orElseThrow(ImageUploadNotFoundException::new);
        if (image.getStatus() != ImageUploadStatus.COMPLETED) throw new UploadStateConflictException();
        return new com.example.demo.media.dto.response.PreparedProductImage(image.getId(), actor, image.getVersion(), image.getFinalKey(), image.getContentType());
    }

    @Override @Transactional
    public void validatePreparedProductImage(Long actor, com.example.demo.media.dto.response.PreparedProductImage prepared) {
        if (actor == null || !actor.equals(prepared.getOwner())) throw new ImageUploadNotFoundException();
        java.util.List<Object[]> rows = repository.lockProductAttachmentSnapshot(prepared.getId());
        if (rows.size() != 1) throw new ImageUploadNotFoundException();
        Object[] image = rows.getFirst();
        if (!actor.equals(((Number) image[1]).longValue())) throw new ImageUploadNotFoundException();
        if (!prepared.getId().equals(image[0]) || !"COMPLETED".equals(image[5])
                || !java.util.Objects.equals(((Number) image[2]).longValue(), prepared.getVersion())
                || !java.util.Objects.equals(image[3], prepared.getFinalKey())
                || !java.util.Objects.equals(image[4], prepared.getContentType())) throw new UploadStateConflictException();
    }

    @Override @Transactional(readOnly = true)
    public com.example.demo.media.dto.response.PreparedReviewImages prepareReviewImages(Long actor, java.util.List<UUID> ids) {
        if (actor == null || actor <= 0) throw new IdentityUnavailableException();
        java.util.Map<UUID, String> keys = new java.util.HashMap<>();
        for (ImageUpload image : repository.findAllById(ids)) {
            if (!actor.equals(image.getOwnerUserId())) throw new ImageUploadNotFoundException();
            if (image.getStatus() != ImageUploadStatus.COMPLETED) throw new UploadStateConflictException();
            keys.put(image.getId(), image.getFinalKey());
        }
        if (keys.size() != ids.size()) throw new ImageUploadNotFoundException();
        for (String key : keys.values()) storage.verifyFinalExists(key);
        return new com.example.demo.media.dto.response.PreparedReviewImages(keys);
    }

    @Override @Transactional
    public void validatePreparedReviewImages(Long actor, com.example.demo.media.dto.response.PreparedReviewImages prepared) {
        for (UUID id : prepared.getFinalKeys().keySet().stream().sorted().toList()) {
            ImageUpload image = repository.findForReviewAttachment(id).orElseThrow(ImageUploadNotFoundException::new);
            if (!actor.equals(image.getOwnerUserId())) throw new ImageUploadNotFoundException();
            if (image.getStatus() != ImageUploadStatus.COMPLETED || !prepared.getFinalKeys().get(id).equals(image.getFinalKey()))
                throw new UploadStateConflictException();
        }
    }

    @Override @Transactional(readOnly = true)
    public java.util.Map<UUID, ImageAccessResponse> getSafeAttachedImages(java.util.Collection<UUID> ids) {
        java.util.Map<UUID, ImageAccessResponse> result = new java.util.HashMap<>();
        if (ids.isEmpty()) return result;
        ids.forEach(id -> result.put(id, new ImageAccessResponse(id, null)));
        java.util.List<ImageUpload> images = repository.findAllById(ids);
        try {
            com.example.demo.storage.service.PreparedImageUrls signer = imageUrls.prepare();
            for (ImageUpload image : images) {
                if (image.getStatus() != ImageUploadStatus.COMPLETED) continue;
                try { result.put(image.getId(), new ImageAccessResponse(image.getId(), signer.resolve(image.getFinalKey()))); }
                catch (com.example.demo.storage.exception.StorageUnavailableException unavailable) { /* Keep safe null URL. */ }
            }
        } catch (com.example.demo.storage.exception.StorageUnavailableException unavailable) { /* Keep safe null URLs. */ }
        return result;
    }

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
    public ImageAccessResponse validateImageForAttachment(Long actorId, UUID imageId) {
        if (actorId == null || actorId <= 0) throw new IdentityUnavailableException();
        validator.validateId(imageId);
        ImageUpload image = repository.findByIdAndOwnerUserId(imageId, actorId).orElseThrow(ImageUploadNotFoundException::new);
        if (image.getStatus() != ImageUploadStatus.COMPLETED) throw new UploadStateConflictException();
        // Attachment is a mutation boundary: retain the existing physical-object check.
        storage.verifyFinalExists(image.getFinalKey());
        return access(image.getId(), image.getFinalKey());
    }
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
        return converter.toAccess(id, imageUrls.resolve(key));
    }
    private void requireEntryBoundary() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Media completion facade must not join an ambient transaction");
    }
}
