package com.example.demo.auth.dto.request;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class ChangePasswordRequest {
    @jakarta.validation.constraints.NotNull @tools.jackson.databind.annotation.JsonDeserialize(using=com.example.demo.auth.validator.StrictStringDeserializer.class) private String currentPassword;
    @jakarta.validation.constraints.NotNull @tools.jackson.databind.annotation.JsonDeserialize(using=com.example.demo.auth.validator.StrictStringDeserializer.class) private String newPassword;
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String field,tools.jackson.databind.JsonNode value) {
        throw new com.example.demo.user.exception.BusinessValidationException("Unsupported request field");
    }
}
