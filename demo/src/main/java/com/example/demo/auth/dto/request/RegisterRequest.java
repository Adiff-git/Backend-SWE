package com.example.demo.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter @NoArgsConstructor
public class RegisterRequest extends IdentityRequest {
    @NotBlank @Size(max=254) @jakarta.validation.constraints.Email private String email;
    @NotBlank private String password;
    @JsonSetter("email") public void setEmail(JsonNode value){email=text(value).strip();}
    @JsonSetter("password") public void setPassword(JsonNode value){password=text(value);}
}
