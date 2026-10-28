package com.example.demo.media.converter;

import com.example.demo.media.dto.response.ImageAccessResponse;
import com.example.demo.media.dto.response.ImageUploadResponse;
import com.example.demo.media.entity.ImageUpload;
import org.springframework.stereotype.Component;

@Component
public class MediaConverter {
    public ImageUploadResponse toUpload(ImageUpload upload, String url) {
        return new ImageUploadResponse(upload.getId(), url, upload.getExpiresAt());
    }
    public ImageAccessResponse toAccess(Long id, String url) { return new ImageAccessResponse(id, url); }
}
