package com.example.demo.auth.service;

import com.example.demo.auth.config.VerificationProperties;
import com.example.demo.auth.service.impl.IdentityProofServiceImpl;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityProofServiceImplTest {
    private final IdentityProofService proofs = new IdentityProofServiceImpl(properties());
    @Test void verifyCode_shouldBindLeadingZeroCodeToExactContext() {
        UUID challenge = UUID.randomUUID();
        byte[] digest = proofs.codeMac(challenge, "PENDING", UUID.randomUUID().toString(), "user@example.test", "REGISTRATION", "000123");
        assertThat(digest).hasSize(32);
        assertThat(proofs.matches(digest, digest.clone())).isTrue();
        assertThat(proofs.codeMac(challenge, "USER", "1", "other@example.test", "EMAIL_CHANGE", "000123"))
                .isNotEqualTo(digest);
        assertThatThrownBy(() -> proofs.codeMac(challenge, "USER", "1", "user@example.test", "REGISTRATION", "123"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void encrypt_shouldAuthenticatePayloadAndContext() {
        byte[] encrypted = proofs.encrypt("recipient|000123".getBytes(java.nio.charset.StandardCharsets.UTF_8), "challenge-one");
        assertThat(proofs.decrypt(encrypted, "challenge-one")).asString().isEqualTo("recipient|000123");
        assertThatThrownBy(() -> proofs.decrypt(encrypted, "challenge-two")).isInstanceOf(IllegalArgumentException.class);
        encrypted[encrypted.length - 1] ^= 1;
        assertThatThrownBy(() -> proofs.decrypt(encrypted, "challenge-one")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void generate_shouldReturnHighEntropySecretsAndSixDigitCodes() {
        assertThat(proofs.newSecret()).hasSize(43).isNotEqualTo(proofs.newSecret());
        for (int i = 0; i < 30; i++) assertThat(proofs.newCode()).matches("[0-9]{6}");
        assertThat(proofs.hash("one")).hasSize(32).isNotEqualTo(proofs.hash("two"));
    }
    private VerificationProperties properties() {
        VerificationProperties p = new VerificationProperties();
        p.setEnabled(true);
        p.setHmacKey(key(1)); p.setEncryptionKey(key(2));
        p.setMailFrom("sender@example.test"); p.validate();
        return p;
    }
    private String key(int marker) {
        byte[] bytes = new byte[32]; java.util.Arrays.fill(bytes, (byte) marker);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
