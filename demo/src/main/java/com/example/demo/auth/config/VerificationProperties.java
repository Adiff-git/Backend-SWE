package com.example.demo.auth.config;

import jakarta.annotation.PostConstruct;
import java.security.MessageDigest;
import java.util.Base64;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("security.verification")
public class VerificationProperties {
    private boolean enabled;
    private String hmacKey;
    private String encryptionKey;
    private String keyId = "v1";
    private String mailFrom;

    @PostConstruct
    public void validate() {
        if (!enabled) return;
        byte[] hmac = decode(hmacKey);
        byte[] encryption = decode(encryptionKey);
        if (hmac.length < 32 || encryption.length != 32
                || MessageDigest.isEqual(hmac, encryption)) {
            throw new IllegalArgumentException("Independent strong identity keys are required");
        }
        if (keyId == null || !keyId.matches("[A-Za-z0-9_-]{1,32}")
                || mailFrom == null || !mailFrom.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Verification sender and key identifier are required");
        }
    }

    public byte[] hmacBytes() { return decode(hmacKey); }
    public byte[] encryptionBytes() { return decode(encryptionKey); }

    private byte[] decode(String value) {
        try {
            return Base64.getDecoder().decode(value == null ? "" : value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Identity key configuration is invalid");
        }
    }
}
