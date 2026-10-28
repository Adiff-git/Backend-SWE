package com.example.demo.media.dto.response;

import java.util.UUID;

import java.time.Instant;
import lombok.Value;
import lombok.ToString;

@Value
public class ImageUploadResponse {
    UUID id;
    @ToString.Exclude String uploadUrl;
    Instant expiresAt;
}
