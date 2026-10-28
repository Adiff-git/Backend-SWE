package com.example.demo.media.integration;

import com.example.demo.media.dto.request.CreateImageUploadRequest;
import com.example.demo.media.dto.response.ImageAccessResponse;
import com.example.demo.media.dto.response.ImageUploadResponse;
import com.example.demo.media.repository.ImageUploadRepository;
import com.example.demo.media.service.MediaService;
import com.example.demo.media.testdata.MediaTestData;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.testdata.UserTestData;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ImageUploadFlowIT extends MediaInfrastructureTestSupport {
    @Autowired MediaService media;
    @Autowired UserRepository users;
    @Autowired ImageUploadRepository images;
    @Autowired S3Client s3;

    @ParameterizedTest
    @CsvSource({"valid.jpg,image/jpeg", "valid.png,image/png", "lossy.webp,image/webp"})
    void upload_shouldCompleteAndViewWithoutApplicationTokenAtStorage_whenSupported(String fixture, String type) throws Exception {
        Long owner = users.saveAndFlush(UserTestData.user("Owner")).getId();
        CreateImageUploadRequest request = new CreateImageUploadRequest();
        request.setContentType(type);
        ImageUploadResponse upload = media.createImageUpload(owner, request);
        byte[] bytes = MediaTestData.fixture(fixture);
        try (HttpClient browser = HttpClient.newHttpClient()) {
            HttpResponse<Void> put = browser.send(HttpRequest.newBuilder(URI.create(upload.getUploadUrl()))
                    .header("Content-Type", type).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(put.statusCode()).isBetween(200, 299);
            ImageAccessResponse completed = media.completeImageUpload(owner, upload.getId());
            HttpResponse<byte[]> viewed = browser.send(HttpRequest.newBuilder(URI.create(completed.getFinalUrl())).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(viewed.statusCode()).isEqualTo(200);
            assertThat(viewed.body()).isEqualTo(bytes);
            String finalKey = images.findById(upload.getId()).orElseThrow().getFinalKey();
            assertThat(media.completeImageUpload(owner, upload.getId()).getId()).isEqualTo(upload.getId());
            assertThat(images.findById(upload.getId()).orElseThrow().getFinalKey()).isEqualTo(finalKey);
            assertThat(media.getImageDownload(owner, upload.getId()).getId()).isEqualTo(upload.getId());
            assertThat(s3.headObject(builder -> builder.bucket(bucket).key(finalKey)).contentType()).isEqualTo(type);
        }
    }
}
