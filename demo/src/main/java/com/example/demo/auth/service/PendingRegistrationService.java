package com.example.demo.auth.service;

import com.example.demo.auth.dto.response.PendingRegistrationResponse;
import java.util.UUID;

public interface PendingRegistrationService {
    PendingRegistrationResponse initiate(String email,String password);
    PendingRegistrationResponse resend(String credential,UUID challengeId);
    IssuedTokens confirm(String credential,UUID challengeId,String code,String password);
}
