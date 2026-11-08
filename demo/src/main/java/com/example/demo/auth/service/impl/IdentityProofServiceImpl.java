package com.example.demo.auth.service.impl;

import com.example.demo.auth.config.VerificationProperties;
import com.example.demo.auth.service.IdentityProofService;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "security.verification.enabled", havingValue = "true")
public class IdentityProofServiceImpl implements IdentityProofService {
    private static final int NONCE_BYTES = 12;
    private final SecureRandom random = new SecureRandom();
    private final byte[] hmac;
    private final byte[] encryption;

    public IdentityProofServiceImpl(VerificationProperties properties) {
        properties.validate();
        hmac = properties.hmacBytes();
        encryption = properties.encryptionBytes();
    }
    public String newSecret() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public String newCode() { return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }
    public byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException impossible) { throw new IllegalStateException(impossible); }
    }
    public byte[] codeMac(UUID challenge, String ownerKind, String owner, String destination, String purpose, String code) {
        if (code == null || !code.matches("[0-9]{6}")) throw new IllegalArgumentException("Invalid verification code shape");
        return mac(hmac, challenge.toString(), ownerKind, owner, destination, purpose, code);
    }
    public boolean matches(byte[] first, byte[] second) {
        return first != null && second != null && MessageDigest.isEqual(first, second);
    }
    private byte[] mac(byte[] key, String... components) {
        try {
            Mac function = Mac.getInstance("HmacSHA256");
            function.init(new SecretKeySpec(key, "HmacSHA256"));
            for (String value : components) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                function.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                function.update(bytes);
            }
            return function.doFinal();
        } catch (GeneralSecurityException impossible) { throw new IllegalStateException(impossible); }
    }
    public byte[] encrypt(byte[] plaintext, String context) {
        byte[] nonce = new byte[NONCE_BYTES]; random.nextBytes(nonce);
        byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, plaintext, nonce, context);
        return ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array();
    }
    public byte[] decrypt(byte[] encrypted, String context) {
        if (encrypted == null || encrypted.length < NONCE_BYTES + 16) throw new IllegalArgumentException("Invalid protected identity data");
        ByteBuffer buffer = ByteBuffer.wrap(encrypted);
        byte[] nonce = new byte[NONCE_BYTES]; buffer.get(nonce);
        byte[] ciphertext = new byte[buffer.remaining()]; buffer.get(ciphertext);
        return crypt(Cipher.DECRYPT_MODE, ciphertext, nonce, context);
    }
    private byte[] crypt(int mode, byte[] value, byte[] nonce, String context) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(encryption, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(value);
        } catch (GeneralSecurityException failure) { throw new IllegalArgumentException("Invalid protected identity data"); }
    }
}
