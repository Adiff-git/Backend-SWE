package com.example.demo.media.service;

import java.util.UUID;

import com.example.demo.media.dto.request.CreateImageUploadRequest;
import com.example.demo.media.dto.response.ImageAccessResponse;
import com.example.demo.media.dto.response.ImageUploadResponse;

public interface MediaService {
    ImageUploadResponse createImageUpload(Long actorId, CreateImageUploadRequest request);
    ImageAccessResponse completeImageUpload(Long actorId, UUID imageId);
    ImageAccessResponse getImageDownload(Long actorId, UUID imageId);
    ImageAccessResponse validateImageForAttachment(Long actorId, UUID imageId);
    ImageAccessResponse getAttachedImage(UUID imageId);
}
