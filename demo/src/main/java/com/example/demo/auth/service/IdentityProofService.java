package com.example.demo.auth.service;

import java.util.UUID;

public interface IdentityProofService {
    String newSecret();
    String newCode();
    byte[] hash(String value);
    byte[] codeMac(UUID challenge, String ownerKind, String owner, String destination, String purpose, String code);
    boolean matches(byte[] first, byte[] second);
    byte[] encrypt(byte[] plaintext, String context);
    byte[] decrypt(byte[] encrypted, String context);
}
