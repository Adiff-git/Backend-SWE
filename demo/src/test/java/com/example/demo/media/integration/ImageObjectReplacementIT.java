package com.example.demo.media.integration;

import com.example.demo.media.dto.response.ImageUploadResponse;
import com.example.demo.media.enums.ImageUploadStatus;
import com.example.demo.media.testdata.MediaTestData;
import com.example.demo.storage.exception.StoredObjectChangedException;
import com.example.demo.storage.service.ObjectStorageService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import software.amazon.awssdk.core.sync.RequestBody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class ImageObjectReplacementIT extends SeededMediaTestSupport {
    @MockitoSpyBean ObjectStorageService storage;

    @Test void complete_shouldRejectDifferentBytesReplacedAfterDecode() {
        Long owner = owner();
        ImageUploadResponse image = prepare(owner);
        String staging = stage(image);
        doAnswer(invocation -> {
            s3.putObject(request -> request.bucket(bucket).key(staging).contentType("image/png"),
                    RequestBody.fromBytes(MediaTestData.fixture("valid.jpg")));
            return invocation.callRealMethod();
        }).when(storage).copyIfMatch(any(), any(), any(), any());
        assertThatThrownBy(() -> media.completeImageUpload(owner, image.getId()))
                .isInstanceOf(StoredObjectChangedException.class);
        assertThat(images.findById(image.getId()).orElseThrow().getStatus()).isEqualTo(ImageUploadStatus.PENDING);
        assertThat(s3.listObjectsV2(request -> request.bucket(bucket).prefix("images/")).contents()).isEmpty();
    }

    @Test void complete_shouldRejectReplacementBeforeConditionalRead() {
        Long owner = owner();
        ImageUploadResponse image = prepare(owner);
        String staging = stage(image);
        doAnswer(invocation -> {
            s3.putObject(request -> request.bucket(bucket).key(staging).contentType("image/png"),
                    RequestBody.fromBytes(MediaTestData.fixture("valid.jpg")));
            return invocation.callRealMethod();
        }).when(storage).readIfMatch(any(), any());
        assertThatThrownBy(() -> media.completeImageUpload(owner, image.getId()))
                .isInstanceOf(StoredObjectChangedException.class);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void complete_shouldAllowIdenticalContentAndIgnoreUnapprovedMetadata(boolean replaceMetadata) {
        Long owner = owner();
        ImageUploadResponse image = prepare(owner);
        String staging = stage(image);
        doAnswer(invocation -> {
            s3.putObject(request -> request.bucket(bucket).key(staging)
                            .contentType(replaceMetadata ? "text/plain" : "image/png")
                            .metadata(Map.of("unapproved", "value")),
                    RequestBody.fromBytes(MediaTestData.fixture("valid.png")));
            return invocation.callRealMethod();
        }).when(storage).copyIfMatch(any(), any(), any(), any());
        assertThat(media.completeImageUpload(owner, image.getId()).getId()).isEqualTo(image.getId());
        String finalKey = images.findById(image.getId()).orElseThrow().getFinalKey();
        assertThat(s3.headObject(request -> request.bucket(bucket).key(finalKey)).contentType()).isEqualTo("image/png");
        assertThat(s3.headObject(request -> request.bucket(bucket).key(finalKey)).metadata()).isEmpty();
    }
}
