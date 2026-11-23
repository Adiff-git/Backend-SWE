package com.example.demo.media.service;

import java.util.UUID;

import com.example.demo.media.dto.request.CreateImageUploadRequest;
import com.example.demo.media.dto.response.ImageAccessResponse;
import com.example.demo.media.dto.response.ImageUploadResponse;

public interface MediaService {
    com.example.demo.media.dto.response.PreparedProductImage prepareProductImage(Long actor, UUID id);
    void validatePreparedProductImage(Long actor, com.example.demo.media.dto.response.PreparedProductImage prepared);

    com.example.demo.media.dto.response.PreparedReviewImages prepareReviewImages(Long actor, java.util.List<UUID> ids);
    void validatePreparedReviewImages(Long actor, com.example.demo.media.dto.response.PreparedReviewImages prepared);
    java.util.Map<UUID, ImageAccessResponse> getSafeAttachedImages(java.util.Collection<UUID> ids);
    ImageUploadResponse createImageUpload(Long actorId, CreateImageUploadRequest request);
    ImageAccessResponse completeImageUpload(Long actorId, UUID imageId);
    ImageAccessResponse getImageDownload(Long actorId, UUID imageId);
    ImageAccessResponse validateImageForAttachment(Long actorId, UUID imageId);
    ImageAccessResponse getAttachedImage(UUID imageId);
}
