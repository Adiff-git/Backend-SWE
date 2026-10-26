package com.example.demo.auth.converter;
import com.example.demo.auth.service.IssuedTokens;
import com.example.demo.auth.dto.response.TokenResponse;
import org.springframework.stereotype.Component;
@Component
public class AuthConverter {
    public TokenResponse toResponse(IssuedTokens tokens) {
        return tokens.getResponse();
    }
}
