package com.example.demo.auth.converter;

import com.example.demo.auth.dto.response.EmailVerificationChallengeResponse;
import com.example.demo.auth.entity.EmailVerificationChallenge;
import org.springframework.stereotype.Component;

@Component
public class EmailVerificationConverter {
    public EmailVerificationChallengeResponse toResponse(EmailVerificationChallenge challenge){
        return new EmailVerificationChallengeResponse(challenge.getId(),challenge.getPurpose().name(),challenge.getExpiresAt(),challenge.getDeliveryStatus().name());
    }
}
