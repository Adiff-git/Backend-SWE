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
        ImageAccessResponse prepared = new ImageAccessResponse(2L, "prepared-access");
        com.example.demo.user.dto.response.PublicUserResponse result = new UserConverter().toPublic(user, prepared);
        assertThat(result.getProfileImageId()).isEqualTo(2L);
        assertThat(result.getProfileImageUrl()).isEqualTo("prepared-access");
    }
}
