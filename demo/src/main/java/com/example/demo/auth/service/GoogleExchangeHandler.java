package com.example.demo.auth.service;

import com.example.demo.auth.entity.OAuthHandoff;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Purpose-specific transactional business workflow invoked by the exchange coordinator. */
public interface GoogleExchangeHandler {
    OAuthHandoff.Purpose purpose();
    GoogleExchangeOutcome exchange(UUID handoff,byte[] codeHash,String verifier,Jwt bearer);
}
