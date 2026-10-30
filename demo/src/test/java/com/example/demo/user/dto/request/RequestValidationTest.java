package com.example.demo.user.dto.request;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class RequestValidationTest {
    @Test void request_shouldStripBeforeCountingCodePoints() {
        UpdateUserRequest r=new UpdateUserRequest();
        r.setDisplayName(" "+"\uD83D\uDE00".repeat(100)+" ");
        try(ValidatorFactory f=Validation.buildDefaultValidatorFactory()) {
            assertThat(f.getValidator().validate(r)).isEmpty();
            r.setDisplayName("\uD83D\uDE00".repeat(101));
            assertThat(f.getValidator().validate(r)).hasSize(1);
        }
    }
    @Test void request_shouldBindUuidImageIdForServiceValidation() {
        UpdateUserRequest r=new UpdateUserRequest();
        r.setProfileImageId(JsonMapper.builder().build().readTree("\"00000000-0000-4000-8000-000000000001\""));
        assertThat(r.getProfileImageId()).isEqualTo(java.util.UUID.fromString("00000000-0000-4000-8000-000000000001"));
    }
    @Test void request_shouldRejectInternalSpacesInPhone() {
        CreateAddressRequest r=new CreateAddressRequest();
        r.setRecipientName(" Name ");
        r.setAddressText(" Street ");
        r.setRecipientPhone("01234 6789");
        try(ValidatorFactory f=Validation.buildDefaultValidatorFactory()) {
            assertThat(f.getValidator().validate(r)).hasSize(1);
        }
    }
    @Test
    void request_shouldRejectProtectedFieldBinding() {
        assertThatThrownBy(() -> JsonMapper.builder().build().readValue("{\"password\":\"never accepted\"}", UpdateUserRequest.class)).hasRootCauseInstanceOf(com.example.demo.user.exception.BusinessValidationException.class);
    }
}
