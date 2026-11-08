package com.example.demo.auth.validator;

import com.example.demo.auth.dto.request.RegisterRequest;
import com.example.demo.auth.exception.IdentityException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthValidatorTest {
    private final JsonMapper json=JsonMapper.builder().build();
    @Test void registration_shouldNormalizeOnlyEmailAndPreserveExactPassword(){
        RegisterRequest request=json.readValue("{\"email\":\"  Alice@Example.com  \",\"password\":\" exact 123 \"}",RegisterRequest.class);
        try(jakarta.validation.ValidatorFactory factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){
            PendingRegistrationValidator validator=new PendingRegistrationValidator(new PasswordPolicyValidator(),factory.getValidator());
            assertThat(validator.normalizeEmail(request.getEmail())).isEqualTo("alice@example.com");assertThat(request.getPassword()).isEqualTo(" exact 123 ");
            for(String invalid:java.util.List.of("bad@name","first,second@example.com","first..second@example.com","first@example.com\r\nBcc:other@example.com"))
                assertThatThrownBy(()->validator.normalizeEmail(invalid)).isInstanceOf(IdentityException.class);
        }
        assertThatThrownBy(()->json.readValue("{\"email\":42,\"password\":\"Password 123\"}",RegisterRequest.class)).isInstanceOf(RuntimeException.class);
    }
}
