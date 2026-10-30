package com.example.demo.common.exception;
import com.example.demo.user.integration.PostgresTestSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest
class ApiErrorSerializationIT extends PostgresTestSupport {
    @Autowired JsonMapper mapper;
    @Test
    void serialize_shouldExposeProblemDetailAndExtensionsAtTopLevel() {
        ApiError error=new ApiErrorFactory().create(400,"Invalid input","/api/me",Map.of("username",java.util.List.of("must not be blank")));
        JsonNode json=mapper.readTree(mapper.writeValueAsString(error));
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("message").asString()).isEqualTo(json.get("detail").asString());
        assertThat(json.get("path").asString()).isEqualTo(json.get("instance").asString());
        assertThat(json.get("fieldErrors").get("username").get(0).asString()).isEqualTo("must not be blank");
        assertThat(json.get("timestamp").asString()).endsWith("Z");
    }
}
