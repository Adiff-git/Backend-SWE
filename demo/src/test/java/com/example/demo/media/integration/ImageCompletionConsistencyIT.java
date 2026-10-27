package com.example.demo.media.integration;

import com.example.demo.media.dto.response.ImageUploadResponse;
import com.example.demo.media.enums.ImageUploadStatus;
import com.example.demo.media.exception.InvalidImageContentException;
import com.example.demo.media.exception.UploadExpiredException;
import com.example.demo.media.exception.UploadStateConflictException;
import com.example.demo.media.repository.ImageUploadRepository;
import com.example.demo.storage.exception.StorageUnavailableException;
import com.example.demo.storage.service.ObjectStorageService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import software.amazon.awssdk.core.sync.RequestBody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doCallRealMethod;

@SpringBootTest
class ImageCompletionConsistencyIT extends SeededMediaTestSupport {
    @MockitoSpyBean ObjectStorageService storage;
    @MockitoSpyBean ImageUploadRepository uploadRepository;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean java.time.Clock clock;
    @MockitoSpyBean com.example.demo.media.validator.ImageContentValidator decoder;

    @Test void complete_shouldPersistExpired_whenValidationCrossesDeadline() {
        Long owner = owner();
        ImageUploadResponse image = prepare(owner);
        stage(image);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.callRealMethod();
            org.mockito.Mockito.doReturn(image.getExpiresAt()).when(clock).instant();
            return null;
        }).when(decoder).validate(any(), any());
        assertThatThrownBy(() -> media.completeImageUpload(owner, image.getId())).isInstanceOf(UploadExpiredException.class);
        assertThat(images.findById(image.getId()).orElseThrow().getStatus()).isEqualTo(ImageUploadStatus.EXPIRED);
        assertThat(images.findById(image.getId()).orElseThrow().getFinalKey()).isNull();
    }

    @Test void complete_shouldRetainPendingAndStaging_whenCopyUnavailable() {
        Long owner = owner();
        ImageUploadResponse image = prepare(owner);
        stage(image);
        doThrow(new StorageUnavailableException()).when(storage).copyIfMatch(any(), any(), any(), any());
        assertThatThrownBy(() -> media.completeImageUpload(owner, image.getId())).isInstanceOf(StorageUnavailableException.class);
        assertThat(images.findById(image.getId()).orElseThrow().getStatus()).isEqualTo(ImageUploadStatus.PENDING);
        assertThat(s3.listObjectsV2(request -> request.bucket(bucket).prefix("staging/")).contents()).hasSize(1);
    }

    @Test void complete_shouldKeepCompletedImageUsableAfterOriginalDeadline() {
        Long owner = owner();
        ImageUploadResponse image = prepare(owner);
        stage(image);
        media.completeImageUpload(owner, image.getId());
        jdbc.update("UPDATE image_uploads SET created_at=?, expires_at=? WHERE id=?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1800)),
                java.sql.Timestamp.from(Instant.now().minusSeconds(900)), image.getId());
        assertThat(media.completeImageUpload(owner, image.getId()).getId()).isEqualTo(image.getId());
        assertThat(media.getImageDownload(owner, image.getId()).getId()).isEqualTo(image.getId());
    }

    @Test void complete_shouldPersistExpiredDespiteReturnedError() {
        Long owner = owner();
        ImageUploadResponse image = prepare(owner);
        jdbc.update("UPDATE image_uploads SET created_at=?, expires_at=? WHERE id=?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1800)),
                java.sql.Timestamp.from(Instant.now().minusSeconds(900)), image.getId());
        assertThatThrownBy(() -> media.completeImageUpload(owner, image.getId())).isInstanceOf(UploadExpiredException.class);
        assertThat(images.findById(image.getId()).orElseThrow().getStatus()).isEqualTo(ImageUploadStatus.EXPIRED);
    }

    @Test void complete_shouldLeavePending_whenStagingMissingOrInvalid() {
        Long owner = owner();
        ImageUploadResponse image = prepare(owner);
        assertThatThrownBy(() -> media.completeImageUpload(owner, image.getId())).isInstanceOf(UploadStateConflictException.class);
        String staging = images.findById(image.getId()).orElseThrow().getStagingKey();
        s3.putObject(request -> request.bucket(bucket).key(staging).contentType("image/png"), RequestBody.empty());
        assertThatThrownBy(() -> media.completeImageUpload(owner, image.getId())).isInstanceOf(InvalidImageContentException.class);
        assertThat(images.findById(image.getId()).orElseThrow().getStatus()).isEqualTo(ImageUploadStatus.PENDING);
    }

    @Test void complete_shouldRollbackDatabaseButRetainCopyAndStaging_whenDatabaseFailsAfterCopy() {
        Long owner = owner();
        ImageUploadResponse image = prepare(owner);
        stage(image);
        doThrow(new DataAccessResourceFailureException("controlled failure")).when(uploadRepository).flush();
        assertThatThrownBy(() -> media.completeImageUpload(owner, image.getId())).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(images.findById(image.getId()).orElseThrow().getStatus()).isEqualTo(ImageUploadStatus.PENDING);
        assertThat(s3.listObjectsV2(request -> request.bucket(bucket).prefix("images/")).contents()).hasSize(1);
        assertThat(s3.listObjectsV2(request -> request.bucket(bucket).prefix("staging/")).contents()).hasSize(1);
    }

    @Test void complete_shouldSucceedDespitePostCommitDeletionFailure() {
        Long owner = owner();
        ImageUploadResponse image = prepare(owner);
        stage(image);
        doThrow(new StorageUnavailableException()).when(storage).deleteStaging(any());
        assertThat(media.completeImageUpload(owner, image.getId()).getId()).isEqualTo(image.getId());
        assertThat(images.findById(image.getId()).orElseThrow().getStatus()).isEqualTo(ImageUploadStatus.COMPLETED);
    }

    @Test void complete_shouldRecoverWithoutCopyingAgain_whenPostCommitSigningFails() {
        Long owner = owner();
        ImageUploadResponse image = prepare(owner);
        stage(image);
        doThrow(new StorageUnavailableException()).when(storage).prepareDownload(any(), any());
        assertThatThrownBy(() -> media.completeImageUpload(owner, image.getId())).isInstanceOf(StorageUnavailableException.class);
        String key = images.findById(image.getId()).orElseThrow().getFinalKey();
        doCallRealMethod().when(storage).prepareDownload(any(), any());
        assertThat(media.completeImageUpload(owner, image.getId()).getId()).isEqualTo(image.getId());
        assertThat(images.findById(image.getId()).orElseThrow().getFinalKey()).isEqualTo(key);
    }
}
