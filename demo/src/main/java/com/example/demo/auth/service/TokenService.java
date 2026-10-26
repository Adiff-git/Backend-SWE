package com.example.demo.auth.service;
import com.example.demo.auth.entity.AuthenticationSession;
public interface TokenService {
    String generateRefreshToken();
    byte[] hash(String credential);
    IssuedTokens issue(AuthenticationSession session,String refreshToken);
}
