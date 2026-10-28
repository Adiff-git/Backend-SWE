package com.example.demo.user.converter;

import com.example.demo.media.dto.response.ImageAccessResponse;
import com.example.demo.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserImageConverterTest {
    @Test void toPublic_shouldMapPreparedImageWithoutExposingPrivateFields() {
        User user = new User();
        user.setId(1L);
        user.setEmail("private@example.com");
        ImageAccessResponse prepared = new ImageAccessResponse(java.util.UUID.fromString("00000000-0000-4000-8000-000000000002"), "prepared-access");
        com.example.demo.user.dto.response.PublicUserResponse result = new UserConverter().toPublic(user, prepared);
        assertThat(result.getProfileImageId()).isEqualTo(prepared.getId());
        assertThat(result.getProfileImageUrl()).isEqualTo("prepared-access");
    }
}
