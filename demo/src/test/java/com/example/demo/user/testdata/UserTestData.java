package com.example.demo.user.testdata;
import com.example.demo.user.entity.User;
import com.example.demo.user.enums.AccountStatus;
import java.time.Instant;
public final class UserTestData {
    private UserTestData() {
    }
    public static User user(String username) {
        User u=new User();
        u.setUsername(username);
        u.setPassword("{bcrypt}$2a$10$controlledEncodedTestFixture");
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setJoinedAt(Instant.parse("2026-10-26T00:00:00Z"));
        return u;
    }
}
