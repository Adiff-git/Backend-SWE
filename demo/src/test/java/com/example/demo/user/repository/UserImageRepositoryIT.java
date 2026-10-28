package com.example.demo.user.repository;

import com.example.demo.media.entity.ImageUpload;
import com.example.demo.media.repository.ImageUploadRepository;
import com.example.demo.user.entity.User;
import com.example.demo.user.integration.PostgresTestSupport;
import com.example.demo.user.testdata.UserTestData;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserImageRepositoryIT extends PostgresTestSupport {
    @Autowired UserRepository users;
    @Autowired ImageUploadRepository images;

    @Test void save_shouldPreserveExistingUsersAndRetainImageAfterReferenceClear() {
        User user = users.saveAndFlush(UserTestData.user("AvatarOwner"));
        assertThat(user.getProfileImageId()).isNull();
        Instant now = Instant.now();
        ImageUpload image = ImageUpload.pending(user.getId(), "staging/key", "image/png", now, now.plusSeconds(900));
        image.complete("images/final");
        images.saveAndFlush(image);
        user.setProfileImageId(image.getId());
        users.flush();
        user.setProfileImageId(null);
        users.flush();
        assertThat(images.findById(image.getId())).isPresent();
    }

    @Test void save_shouldRejectMissingReferencedImage() {
        User user = users.saveAndFlush(UserTestData.user("AvatarOwner"));
        user.setProfileImageId(java.util.UUID.randomUUID());
        assertThatThrownBy(users::flush).isInstanceOf(DataIntegrityViolationException.class);
    }
}
