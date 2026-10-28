package com.example.demo.media.repository;

import com.example.demo.media.entity.ImageUpload;
import com.example.demo.media.enums.ImageUploadStatus;
import com.example.demo.user.entity.User;
import com.example.demo.user.integration.PostgresTestSupport;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.testdata.UserTestData;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ImageUploadRepositoryIT extends PostgresTestSupport {
    @Autowired ImageUploadRepository uploads;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    private ImageUpload pending(Long owner, String type) {
        Instant created = Instant.parse("2026-10-27T00:00:00Z");
        return ImageUpload.pending(owner, "staging/" + UUID.randomUUID(), type, created, created.plusSeconds(900));
    }

    @Test void save_shouldPreserveOwnerAndLifecycleAndFindOnlyOwnedUploads() {
        User owner = users.saveAndFlush(UserTestData.user("ImageOwner"));
        ImageUpload upload = uploads.saveAndFlush(pending(owner.getId(), "image/webp"));
        assertThat(upload.getVersion()).isZero();
        assertThat(upload.getStatus()).isEqualTo(ImageUploadStatus.PENDING);
        assertThat(uploads.findByIdAndOwnerUserId(upload.getId(), owner.getId())).isPresent();
        assertThat(uploads.findByIdAndOwnerUserId(upload.getId(), owner.getId() + 1)).isEmpty();
        upload.complete("images/" + UUID.randomUUID());
        uploads.flush();
        assertThat(upload.getVersion()).isEqualTo(1);
    }

    @Test void save_shouldRejectUnsupportedMime() {
        User owner = users.saveAndFlush(UserTestData.user("ImageOwner"));
        assertThatThrownBy(() -> uploads.saveAndFlush(pending(owner.getId(), "text/plain")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void save_shouldRejectMissingOwner() {
        assertThatThrownBy(() -> uploads.saveAndFlush(pending(Long.MAX_VALUE, "image/png")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void save_shouldRejectDuplicateStagingKey() {
        User owner = users.saveAndFlush(UserTestData.user("ImageOwner"));
        ImageUpload first = pending(owner.getId(), "image/png");
        uploads.saveAndFlush(first);
        ImageUpload duplicate = ImageUpload.pending(owner.getId(), first.getStagingKey(), "image/png",
                first.getCreatedAt(), first.getExpiresAt());
        assertThatThrownBy(() -> uploads.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner_user_id=NULL", "staging_key=NULL", "staging_key=' '",
            "staging_key=REPEAT('x',513)", "content_type=NULL", "content_type=REPEAT('x',101)",
            "status=NULL", "status='OTHER'", "created_at=NULL", "expires_at=NULL",
            "expires_at=created_at", "version=NULL", "final_key=' '", "final_key=REPEAT('x',513)",
            "status='COMPLETED'", "final_key='images/not-completed'"})
    void constraint_shouldRejectInvalidPersistedState(String assignment) {
        User owner = users.saveAndFlush(UserTestData.user("ConstraintOwner"));
        ImageUpload image = uploads.saveAndFlush(pending(owner.getId(), "image/png"));
        assertThatThrownBy(() -> jdbc.update("UPDATE image_uploads SET " + assignment + " WHERE id=?", image.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void flush_shouldRejectStaleVersionWithoutParallelCompletion() {
        User owner = users.saveAndFlush(UserTestData.user("VersionOwner"));
        ImageUpload image = uploads.saveAndFlush(pending(owner.getId(), "image/png"));
        jdbc.update("UPDATE image_uploads SET version=version+1 WHERE id=?", image.getId());
        image.expire();
        assertThatThrownBy(uploads::flush).isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    @Test void save_shouldPermitMultipleNullFinalKeysButRejectDuplicateCompletedKey() {
        User owner = users.saveAndFlush(UserTestData.user("FinalKeyOwner"));
        ImageUpload first = uploads.saveAndFlush(pending(owner.getId(), "image/png"));
        ImageUpload second = uploads.saveAndFlush(pending(owner.getId(), "image/png"));
        first.complete("images/same");
        uploads.flush();
        second.complete("images/same");
        assertThatThrownBy(uploads::flush).isInstanceOf(DataIntegrityViolationException.class);
    }
}
