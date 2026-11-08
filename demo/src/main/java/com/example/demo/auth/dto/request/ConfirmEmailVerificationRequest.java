package com.example.demo.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter @NoArgsConstructor
public class ConfirmEmailVerificationRequest extends IdentityRequest {
    @NotBlank @Pattern(regexp="[0-9]{6}") private String code;
    @Pattern(regexp="[A-Za-z0-9_-]{43}") private String recentAuthProof;
    @JsonSetter("code") public void setCode(JsonNode value){code=text(value);}
    @JsonSetter("recentAuthProof") public void setRecentAuthProof(JsonNode value){recentAuthProof=text(value);}
}
