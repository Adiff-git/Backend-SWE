package com.example.demo.user.converter;
import com.example.demo.user.entity.User;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class UserConverterTest {
    @Test void toPrivate_shouldMapPersonalDetailsWithAbsentImage() {
        User u=new User();
        u.setId(8L);
        u.setUsername("Alice");
        u.setEmail("alice@example.com");
        assertThat(new UserConverter().toPrivate(u).getEmail()).isEqualTo("alice@example.com");
        assertThat(new UserConverter().toPrivate(u).getProfileImageId()).isNull();
    }
    @Test void toPublic_shouldExposeOnlyPublicProperties() {
        assertThat(com.example.demo.user.dto.response.PublicUserResponse.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName).containsExactlyInAnyOrder("id","username","displayName","joinedAt","profileImageId","profileImageUrl");
    }
}
