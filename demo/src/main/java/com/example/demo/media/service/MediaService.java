package com.example.demo.media.service;

import com.example.demo.media.dto.request.CreateImageUploadRequest;
import com.example.demo.media.dto.response.ImageAccessResponse;
import com.example.demo.media.dto.response.ImageUploadResponse;

public interface MediaService {
    ImageUploadResponse createImageUpload(Long actorId, CreateImageUploadRequest request);
    ImageAccessResponse completeImageUpload(Long actorId, Long imageId);
    ImageAccessResponse getImageDownload(Long actorId, Long imageId);
    ImageAccessResponse validateImageForAttachment(Long actorId, Long imageId);
    ImageAccessResponse getAttachedImage(Long imageId);
}
