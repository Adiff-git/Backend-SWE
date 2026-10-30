package com.example.demo.auth.service;
import java.util.UUID;
public interface AuthService {
    IssuedTokens register(String username,String password);
    IssuedTokens login(String identifier,String password);
    IssuedTokens refresh(String refreshToken);
    void logout(String refreshToken);
    void changePassword(Long owner,UUID sid,String currentPassword,String newPassword);
}
