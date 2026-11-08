package com.example.demo.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter @NoArgsConstructor
public class ReauthenticationRequest extends IdentityRequest {
    @NotBlank @Pattern(regexp="EMAIL_CHANGE|CONNECT_GOOGLE") private String operation;
    @NotBlank private String password;
    @JsonSetter("operation") public void setOperation(JsonNode value){operation=text(value);}
    @JsonSetter("password") public void setPassword(JsonNode value){password=text(value);}
}
