package com.example.demo.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter
@NoArgsConstructor

public class GoogleCodeExchangeRequest extends IdentityRequest {
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") private String code;
    @NotBlank @Pattern(regexp="[A-Za-z0-9._~-]{43,128}") private String codeVerifier;
    @JsonSetter("code") public void setCode(JsonNode value) { code=text(value); }
    @JsonSetter("codeVerifier") public void setCodeVerifier(JsonNode value) { codeVerifier=text(value); }
}
