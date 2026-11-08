package com.example.demo.auth.service;

import com.example.demo.auth.dto.response.EmailVerificationChallengeResponse;
import com.example.demo.auth.dto.response.VerifiedEmailResponse;
import com.example.demo.auth.entity.EmailVerificationChallenge;
import java.util.UUID;

public interface EmailVerificationService {
    EmailVerificationChallengeResponse create(Long owner,UUID session,EmailVerificationChallenge.Purpose purpose,String destination);
    EmailVerificationChallengeResponse get(Long owner,UUID challenge);
    VerifiedEmailResponse confirm(Long owner,UUID session,UUID challenge,String code,String recentAuthProof);
}
