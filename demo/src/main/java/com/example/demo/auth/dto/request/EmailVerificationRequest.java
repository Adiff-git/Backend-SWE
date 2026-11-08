package com.example.demo.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter @NoArgsConstructor
public class EmailVerificationRequest extends IdentityRequest {
    @NotBlank @Pattern(regexp="REGISTRATION|EMAIL_CHANGE") private String purpose;
    @NotBlank @Size(max=254) @jakarta.validation.constraints.Email private String destination;
    @JsonSetter("purpose") public void setPurpose(JsonNode value){purpose=text(value);}
    @JsonSetter("destination") public void setDestination(JsonNode value){destination=text(value).strip();}
}
