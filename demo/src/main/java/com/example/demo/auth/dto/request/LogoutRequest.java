package com.example.demo.auth.dto.request;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class LogoutRequest {
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.NotEmpty @com.example.demo.user.validator.CodePointLength(max=1024) @tools.jackson.databind.annotation.JsonDeserialize(using=com.example.demo.auth.validator.StrictStringDeserializer.class) private String refreshToken;
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String field,tools.jackson.databind.JsonNode value) {
        throw new com.example.demo.user.exception.BusinessValidationException("Unsupported request field");
    }
}
