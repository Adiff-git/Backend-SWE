package com.example.demo.user.dto.request;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserImageRequestTest {
    private final JsonMapper json = JsonMapper.builder().build();
    @Test void bind_shouldDistinguishOmittedNullAndExplicitImage() {
        assertThat(json.readValue("{}", UpdateUserRequest.class).isProfileImagePresent()).isFalse();
        UpdateUserRequest clear = json.readValue("{\"profileImageId\":null}", UpdateUserRequest.class);
        assertThat(clear.isProfileImagePresent()).isTrue();
        assertThat(clear.getProfileImageId()).isNull();
        assertThat(json.readValue("{\"profileImageId\":123}", UpdateUserRequest.class).getProfileImageId()).isEqualTo(123L);
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "true", "[]", "\"123\"", "\"\"", "9223372036854775808"})
    void bind_shouldRejectMalformedImageId(String value) {
        assertThatThrownBy(() -> json.readValue("{\"profileImageId\":" + value + "}", UpdateUserRequest.class))
                .hasRootCauseInstanceOf(com.example.demo.user.exception.BusinessValidationException.class);
    }
}
