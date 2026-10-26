package com.example.demo.user.converter;
import com.example.demo.user.testdata.UserTestData;
import com.example.demo.user.entity.User;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class PublicProfileConverterTest {
    @Test void toPublic_shouldMapOnlyPublicIdentityAndNullImage() {
        User u=UserTestData.user("Alice");
        u.setId(9L);
        u.setDisplayName("Name");
        u.setEmail("private@example.com");
        assertThat(new UserConverter().toPublic(u).getDisplayName()).isEqualTo("Name");
        assertThat(new UserConverter().toPublic(u).getJoinedAt()).isEqualTo(u.getJoinedAt());
        assertThat(new UserConverter().toPublic(u).getProfileImageId()).isNull();
    }
}
