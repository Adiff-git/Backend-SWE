package com.example.demo.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter @NoArgsConstructor
public class ConfirmRegistrationRequest extends ResendRegistrationRequest {
    @NotBlank @Pattern(regexp="[0-9]{6}") private String code;
    @NotBlank private String password;
    @JsonSetter("code") public void setCode(JsonNode value){code=text(value);}
    @JsonSetter("password") public void setPassword(JsonNode value){password=text(value);}
}
