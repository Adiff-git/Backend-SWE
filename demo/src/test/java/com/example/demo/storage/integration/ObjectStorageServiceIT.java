package com.example.demo.storage.integration;

import com.example.demo.media.dto.response.ImageUploadResponse;
import com.example.demo.media.integration.SeededMediaTestSupport;
import com.example.demo.media.testdata.MediaTestData;
import com.example.demo.storage.service.ObjectStorageService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ObjectStorageServiceIT extends SeededMediaTestSupport {
    @Autowired ObjectStorageService storage;
    @Autowired S3Presigner presigner;

    @Test void presignedPut_shouldRejectWrongContentTypeAndKeepFinalImmutableAfterLaterStagingWrite() throws Exception {
        Long actor = owner();
        ImageUploadResponse image = prepare(actor);
        try (HttpClient browser = HttpClient.newHttpClient()) {
            HttpResponse<Void> wrong = browser.send(HttpRequest.newBuilder(URI.create(image.getUploadUrl()))
                    .header("Content-Type", "image/jpeg")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(MediaTestData.fixture("valid.png"))).build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(wrong.statusCode()).isEqualTo(403);
            stage(image);
            media.completeImageUpload(actor, image.getId());
            String finalKey = images.findById(image.getId()).orElseThrow().getFinalKey();
            HttpResponse<Void> overwrite = browser.send(HttpRequest.newBuilder(URI.create(image.getUploadUrl()))
                    .header("Content-Type", "image/png")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(MediaTestData.fixture("valid.jpg"))).build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(overwrite.statusCode()).isEqualTo(200);
            assertThat(s3.getObjectAsBytes(request -> request.bucket(bucket).key(finalKey)).asByteArray())
                    .isEqualTo(MediaTestData.fixture("valid.png"));
        }
    }

    @Test void presignedGet_shouldExpireWithSignatureValidationEnabled() throws Exception {
        Long actor = owner();
        ImageUploadResponse image = prepare(actor);
        String key = stage(image);
        URI signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(1))
                .getObjectRequest(request -> request.bucket(bucket).key(key)).build()).url().toURI();
        Thread.sleep(2200);
        try (HttpClient browser = HttpClient.newHttpClient()) {
            assertThat(browser.send(HttpRequest.newBuilder(signed).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode())
                    .isEqualTo(403);
        }
    }
}
