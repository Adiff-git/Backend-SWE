package com.example.demo.auth.dto.request;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class RegisterRequest {
    @jakarta.validation.constraints.NotNull @com.example.demo.user.validator.CodePointLength(max=50) @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp="[^@]*") @tools.jackson.databind.annotation.JsonDeserialize(using=com.example.demo.auth.validator.StrictStringDeserializer.class) private String username;
    @jakarta.validation.constraints.NotNull @tools.jackson.databind.annotation.JsonDeserialize(using=com.example.demo.auth.validator.StrictStringDeserializer.class) private String password;
    public void setUsername(String value) {
        username=value==null?null:value.strip();
    }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String field,tools.jackson.databind.JsonNode value) {
        throw new com.example.demo.user.exception.BusinessValidationException("Unsupported request field");
    }
}
