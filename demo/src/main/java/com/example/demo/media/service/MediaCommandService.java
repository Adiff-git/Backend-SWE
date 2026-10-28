package com.example.demo.media.service;

import java.util.UUID;

import com.example.demo.media.dto.request.CreateImageUploadRequest;
import com.example.demo.media.dto.response.ImageUploadResponse;

public interface MediaCommandService {
    ImageUploadResponse create(Long actorId, CreateImageUploadRequest request);
    ImageCompletionOutcome complete(Long actorId, UUID imageId);
}
