package com.example.demo.auth.validator;

import com.example.demo.auth.dto.request.GoogleAttemptRequest;
import com.example.demo.auth.exception.IdentityException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleAttemptValidatorTest {
    @Test void validate_shouldAcceptOnlyCanonicalS256Challenge() {
        GoogleAttemptValidator validator=new GoogleAttemptValidator();
        assertThatCode(()->validator.validateChallenge("A".repeat(43),"S256")).doesNotThrowAnyException();
        assertThatThrownBy(()->validator.validateChallenge("a".repeat(43),"S256")).isInstanceOf(IdentityException.class);
        assertThatThrownBy(()->validator.validateChallenge("A".repeat(43),"plain")).isInstanceOf(IdentityException.class);
    }
    @Test void read_shouldRejectClientOwnerOverridesAndWrongJsonTypes() {
        JsonMapper json=JsonMapper.builder().build();
        assertThatThrownBy(()->json.readValue("{\"codeChallenge\":123,\"codeChallengeMethod\":\"S256\"}",GoogleAttemptRequest.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(()->json.readValue("{\"ownerId\":7}",GoogleAttemptRequest.class)).isInstanceOf(Exception.class);
    }
}
