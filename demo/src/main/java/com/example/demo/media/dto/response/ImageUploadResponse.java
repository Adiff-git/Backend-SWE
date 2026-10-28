package com.example.demo.media.dto.response;

import java.time.Instant;
import lombok.Value;
import lombok.ToString;

@Value
public class ImageUploadResponse {
    Long id;
    @ToString.Exclude String uploadUrl;
    Instant expiresAt;
}
