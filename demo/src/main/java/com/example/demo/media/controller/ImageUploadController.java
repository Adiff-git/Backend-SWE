package com.example.demo.media.controller;

import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.media.dto.request.CreateImageUploadRequest;
import com.example.demo.media.dto.response.ImageAccessResponse;
import com.example.demo.media.dto.response.ImageUploadResponse;
import com.example.demo.media.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/image-uploads")
@RequiredArgsConstructor
public class ImageUploadController {
    private final MediaService media;
    private final CurrentUserIdProvider identity;

    @PostMapping public ResponseEntity<ImageUploadResponse> create(@Valid @RequestBody CreateImageUploadRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(media.createImageUpload(identity.getCurrentUserId(), request));
    }
    @PostMapping("/{imageId}/complete") public ResponseEntity<ImageAccessResponse> complete(@PathVariable Long imageId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(media.completeImageUpload(identity.getCurrentUserId(), imageId));
    }
    @GetMapping("/{imageId}/download-url") public ResponseEntity<ImageAccessResponse> download(@PathVariable Long imageId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(media.getImageDownload(identity.getCurrentUserId(), imageId));
    }
}
