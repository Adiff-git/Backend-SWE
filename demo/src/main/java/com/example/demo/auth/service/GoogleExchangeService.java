package com.example.demo.auth.service;

import com.example.demo.auth.entity.OAuthHandoff;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public interface GoogleExchangeService {
    OAuthHandoff.Purpose purpose(String code);
    GoogleExchangeOutcome exchange(String code,String verifier,Jwt bearer);
}
