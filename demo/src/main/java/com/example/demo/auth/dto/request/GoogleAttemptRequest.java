package com.example.demo.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter
@NoArgsConstructor

public class GoogleAttemptRequest extends IdentityRequest {
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") private String codeChallenge;
    @NotBlank @Pattern(regexp="S256") private String codeChallengeMethod;
    @JsonSetter("codeChallenge") public void setCodeChallenge(JsonNode value) { codeChallenge=text(value); }
    @JsonSetter("codeChallengeMethod") public void setCodeChallengeMethod(JsonNode value) { codeChallengeMethod=text(value); }
}
