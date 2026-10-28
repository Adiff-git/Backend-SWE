package com.example.demo.auth.converter;
import com.example.demo.auth.service.IssuedTokens;
import com.example.demo.auth.dto.response.TokenResponse;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class AuthConverterTest {
    @Test void execute_shouldExposeExactlyFourResponseFieldsAndNoSecretToString() {
        var response=new TokenResponse("Bearer",900,"refresh-secret",Instant.now());
        var issued=new IssuedTokens("access-secret",response);
        String serialized=JsonMapper.builder().build().writeValueAsString(new AuthConverter().toResponse(issued));
        var node=JsonMapper.builder().build().readTree(serialized);
        assertThat(node.propertyNames()).containsExactlyInAnyOrder("tokenType","accessTokenExpiresIn","refreshToken","refreshTokenExpiresAt");
        assertThat(serialized).doesNotContain("access-secret");
        assertThat(issued.toString()).doesNotContain("access-secret","refresh-secret");
        assertThat(response.toString()).doesNotContain("refresh-secret");
    }
}
