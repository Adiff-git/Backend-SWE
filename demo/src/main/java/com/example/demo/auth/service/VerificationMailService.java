package com.example.demo.auth.service;

import com.example.demo.auth.entity.EmailVerificationChallenge;
import java.util.UUID;

public interface VerificationMailService {
    Long enqueue(EmailVerificationChallenge challenge,String code);
    void supersede(UUID challenge);
    void dispatch();
}
