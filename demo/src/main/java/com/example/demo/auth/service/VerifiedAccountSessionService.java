package com.example.demo.auth.service;

public interface VerifiedAccountSessionService {
    IssuedTokens issueForExisting(Long owner);
    IssuedTokens create(String email,String encodedPassword);
}
