package com.example.demo.user.repository;
import com.example.demo.user.testdata.UserTestData;
import com.example.demo.user.entity.User;
import com.example.demo.user.integration.PostgresTestSupport;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@DataJpaTest @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIT extends PostgresTestSupport {
    @Autowired UserRepository users;
    @Test void usernameExists_shouldUseLowercaseKeyAndExcludeCurrentUser() {
        User user=users.saveAndFlush(UserTestData.user("Alice"));
        assertThat(users.usernameExists("ALICE",user.getId())).isFalse();
        assertThat(users.usernameExists("alice",-1L)).isTrue();
    }
    @Test void save_shouldRejectCaseInsensitiveDuplicateAtDatabaseBoundary() {
        users.saveAndFlush(UserTestData.user("Alice"));
        assertThatThrownBy(()->users.saveAndFlush(UserTestData.user("ALICE"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void save_shouldAcceptMultipleAbsentEmailsAndCodePointMaximum() {
        User first=UserTestData.user("First");
        first.setDisplayName("\uD83D\uDE00".repeat(100));
        users.saveAndFlush(first);
        users.saveAndFlush(UserTestData.user("Second"));
        assertThat(users.count()).isEqualTo(2);
    }
    @Test
    void usernameExists_shouldMatchNonAsciiDatabaseLowercase() {
        String upper=String.valueOf((char)0x00c9)+"lodie";
        String lower=String.valueOf((char)0x00e9)+"LODIE";
        User user=users.saveAndFlush(UserTestData.user(upper));
        assertThat(users.usernameExists(lower,-1L)).isTrue();
        assertThat(users.usernameExists(lower,user.getId())).isFalse();
    }
    @Test
    void save_shouldRejectCodePointMaximumPlusOne() {
        User user=UserTestData.user("Owner");user.setDisplayName(new String(Character.toChars(0x1F600)).repeat(101));
        assertThatThrownBy(()->users.saveAndFlush(user)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
