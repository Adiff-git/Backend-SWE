package com.example.demo.auth.dto.request;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class LoginRequest {
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.NotBlank @com.example.demo.user.validator.CodePointLength(max=254) @tools.jackson.databind.annotation.JsonDeserialize(using=com.example.demo.auth.validator.StrictStringDeserializer.class) private String identifier;
    @jakarta.validation.constraints.NotNull @tools.jackson.databind.annotation.JsonDeserialize(using=com.example.demo.auth.validator.StrictStringDeserializer.class) private String password;
    public void setIdentifier(String value) {
        identifier=value==null?null:value.strip();
    }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String field,tools.jackson.databind.JsonNode value) {
        throw new com.example.demo.user.exception.BusinessValidationException("Unsupported request field");
    }
}
