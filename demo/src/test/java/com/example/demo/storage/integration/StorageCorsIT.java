package com.example.demo.storage.integration;

import com.example.demo.media.integration.SeededMediaTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StorageCorsIT extends SeededMediaTestSupport {
    @Test void cors_shouldAllowAllOriginsWithSeparateStorageConfiguration() throws Exception {
        s3.putBucketCors(request -> request.bucket(bucket).corsConfiguration(CORSConfiguration.builder()
                .corsRules(CORSRule.builder().allowedOrigins("*")
                        .allowedMethods("PUT", "GET", "POST", "DELETE", "HEAD").allowedHeaders("*").build()).build()));
        URI endpoint = URI.create(STORAGE.getEndpoint() + "/" + bucket + "/staging/example");
        try (HttpClient browser = HttpClient.newHttpClient()) {
            HttpResponse<Void> allowed = browser.send(HttpRequest.newBuilder(endpoint).header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "PUT").header("Access-Control-Request-Headers", "Content-Type")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            assertThat(allowed.statusCode()).isBetween(200, 299);
            assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains("*");
            HttpResponse<Void> other = browser.send(HttpRequest.newBuilder(endpoint).header("Origin", "http://another.example")
                    .header("Access-Control-Request-Method", "PUT").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(other.headers().firstValue("Access-Control-Allow-Origin")).contains("*");
        }
    }
}
