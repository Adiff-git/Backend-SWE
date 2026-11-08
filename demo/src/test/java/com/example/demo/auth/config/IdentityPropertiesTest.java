package com.example.demo.auth.config;

import java.net.URI;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityPropertiesTest {
    @Test void validate_shouldAllowDisabledFeatureWithoutSecrets() {
        assertThatCode(() -> new IdentityProperties().validate()).doesNotThrowAnyException();
    }
    @Test void validate_shouldRejectInsecureProductionDestinations() {
        IdentityProperties properties = valid();
        properties.setCallbackUri(URI.create("http://localhost:8080/login/oauth2/code/google"));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void validate_shouldRejectArbitraryRedirectsAndWeakKeys() {
        IdentityProperties properties = valid();
        properties.setFrontendSuccessUri(URI.create("https://frontend.example/callback#secret"));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
        VerificationProperties verification = new VerificationProperties();
        verification.setEnabled(true);
        verification.setHmacKey(Base64.getEncoder().encodeToString(new byte[16]));
        assertThatThrownBy(verification::validate).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void validate_shouldRequireIndependentKeys() {
        VerificationProperties properties = new VerificationProperties();
        properties.setEnabled(true);
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        properties.setHmacKey(key);
        properties.setEncryptionKey(key);
        properties.setMailFrom("sender@example.test");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
    }
    private IdentityProperties valid() {
        IdentityProperties properties = new IdentityProperties();
        properties.setEnabled(true);
        properties.setClientId("controlled-client");
        properties.setClientSecret("controlled-secret");
        properties.setCallbackUri(URI.create("https://api.example/login/oauth2/code/google"));
        properties.setFrontendSuccessUri(URI.create("https://frontend.example/callback"));
        properties.setFrontendFailureUri(URI.create("https://frontend.example/failure"));
        properties.setAllowedOrigins(java.util.List.of("https://frontend.example"));
        return properties;
    }
}
