package com.example.demo.auth.validator;
import com.example.demo.auth.dto.request.RegisterRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AuthValidatorTest {
    @Test void execute_shouldNormalizeUsernameOnlyAndPreservesNonAsciiCaseAndWhitespace() {
        RegisterRequest r=new RegisterRequest();
        r.setUsername("  \u00c9 A  B  ");
        r.setPassword(" exact ");
        assertThat(r.getUsername()).isEqualTo("\u00c9 A  B");
        assertThat(r.getPassword()).isEqualTo(" exact ");
        try(var f=Validation.buildDefaultValidatorFactory()) {
            assertThat(f.getValidator().validate(r)).isEmpty();
            r.setUsername("name@example.com");
            assertThat(f.getValidator().validate(r)).isNotEmpty();
            r.setUsername("a".repeat(51));
            assertThat(f.getValidator().validate(r)).isNotEmpty();
            r.setUsername(" ");
            assertThat(f.getValidator().validate(r)).isNotEmpty();
        }
    }
}
