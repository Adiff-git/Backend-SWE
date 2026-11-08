package com.example.demo.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter @NoArgsConstructor
public class ResendRegistrationRequest extends IdentityRequest {
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") private String registrationCredential;
    @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") private String challengeId;
    @JsonSetter("registrationCredential") public void setRegistrationCredential(JsonNode value){registrationCredential=text(value);}
    @JsonSetter("challengeId") public void setChallengeId(JsonNode value){challengeId=text(value);}
}
