package com.example.demo.media.converter;

import com.example.demo.media.entity.ImageUpload;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaConverterTest {
    @Test void toUpload_shouldMapDeadlineAndKeepCapabilityOutOfStringRepresentation() {
        Instant created = Instant.parse("2026-10-27T00:00:00Z");
        ImageUpload image = ImageUpload.pending(1L, "staging/key", "image/png", created, created.plusSeconds(900));
        com.example.demo.media.dto.response.ImageUploadResponse response = new MediaConverter().toUpload(image, "secret-url");
        assertThat(response.getExpiresAt()).isEqualTo(created.plusSeconds(900));
        assertThat(response.getUploadUrl()).isEqualTo("secret-url");
        assertThat(response.toString()).doesNotContain("secret-url");
        assertThat(new MediaConverter().toAccess(1L, "secret-download").toString()).doesNotContain("secret-download");
    }
}
