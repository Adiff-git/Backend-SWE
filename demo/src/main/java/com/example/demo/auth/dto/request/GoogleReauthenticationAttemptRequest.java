package com.example.demo.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter @NoArgsConstructor
public class GoogleReauthenticationAttemptRequest extends GoogleAttemptRequest {
    @NotBlank @Pattern(regexp="EMAIL_CHANGE|INITIAL_PASSWORD|CONNECT_GOOGLE") private String operation;
    @JsonSetter("operation") public void setOperation(JsonNode value){operation=text(value);}
}
