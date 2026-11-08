package com.example.demo.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter @NoArgsConstructor
public class InitialPasswordRequest extends IdentityRequest {
    @NotBlank private String newPassword;
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") private String recentAuthProof;
    @JsonSetter("newPassword") public void setNewPassword(JsonNode value){newPassword=text(value);}
    @JsonSetter("recentAuthProof") public void setRecentAuthProof(JsonNode value){recentAuthProof=text(value);}
}
