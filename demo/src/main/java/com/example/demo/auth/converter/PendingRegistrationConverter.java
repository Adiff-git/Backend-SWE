package com.example.demo.auth.converter;

import com.example.demo.auth.dto.response.PendingRegistrationResponse;
import com.example.demo.auth.entity.EmailVerificationChallenge;
import org.springframework.stereotype.Component;

@Component
public class PendingRegistrationConverter {
    public PendingRegistrationResponse toResponse(String credential,EmailVerificationChallenge challenge){
        return new PendingRegistrationResponse(credential,challenge.getId(),challenge.getExpiresAt());
    }
}
