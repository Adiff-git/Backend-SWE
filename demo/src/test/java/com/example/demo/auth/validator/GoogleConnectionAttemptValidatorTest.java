package com.example.demo.auth.validator;

import com.example.demo.auth.dto.request.GoogleConnectionAttemptRequest;
import com.example.demo.auth.dto.request.GoogleReauthenticationAttemptRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleConnectionAttemptValidatorTest {
    private final JsonMapper json=JsonMapper.builder().build();
    @Test void connection_shouldRequireProofAndRejectOwnerProviderAndDestinationOverrides(){
        String shape="\"codeChallenge\":\""+"A".repeat(43)+"\",\"codeChallengeMethod\":\"S256\"";
        try(jakarta.validation.ValidatorFactory factory=Validation.buildDefaultValidatorFactory()){
            assertThat(factory.getValidator().validate(json.readValue("{"+shape+"}",GoogleConnectionAttemptRequest.class))).isNotEmpty();
            GoogleConnectionAttemptRequest valid=json.readValue("{"+shape+",\"recentAuthProof\":\""+"a".repeat(43)+"\"}",GoogleConnectionAttemptRequest.class);
            assertThat(factory.getValidator().validate(valid)).isEmpty();
            for(String extra:java.util.List.of("\"owner\":7","\"provider\":\"GOOGLE\"","\"destination\":\"https://evil.example\"","\"recentAuthProof\":42"))
                assertThatThrownBy(()->json.readValue("{"+shape+","+extra+"}",GoogleConnectionAttemptRequest.class)).isInstanceOf(RuntimeException.class);
        }
    }
    @Test void reauthentication_shouldSupportOnlyApprovedOperations(){
        try(jakarta.validation.ValidatorFactory factory=Validation.buildDefaultValidatorFactory()){
            for(String operation:java.util.List.of("EMAIL_CHANGE","INITIAL_PASSWORD","CONNECT_GOOGLE")){
                GoogleReauthenticationAttemptRequest request=json.readValue("{\"codeChallenge\":\""+"A".repeat(43)+"\",\"codeChallengeMethod\":\"S256\",\"operation\":\""+operation+"\"}",GoogleReauthenticationAttemptRequest.class);
                assertThat(factory.getValidator().validate(request)).isEmpty();
            }
            GoogleReauthenticationAttemptRequest unsupported=json.readValue("{\"codeChallenge\":\""+"A".repeat(43)+"\",\"codeChallengeMethod\":\"S256\",\"operation\":\"LOGIN\"}",GoogleReauthenticationAttemptRequest.class);
            assertThat(factory.getValidator().validate(unsupported)).isNotEmpty();
        }
    }
}
