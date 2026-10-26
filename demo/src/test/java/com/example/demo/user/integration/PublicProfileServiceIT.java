package com.example.demo.user.integration;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.service.UserService;
import com.example.demo.user.testdata.UserTestData;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest
class PublicProfileServiceIT extends PostgresTestSupport {
    @Autowired UserRepository users;
    @Autowired UserService service;
    @AfterEach void cleanup() {
        users.deleteAll();
    }
    @Test void getUser_shouldMapCorePublicProfileWithNoMediaOrAuthentication() {
        Long id=users.saveAndFlush(UserTestData.user("Alice")).getId();
        assertThat(service.getUser(id).getUsername()).isEqualTo("Alice");
        assertThat(service.getUser(id).getProfileImageId()).isNull();
    }
}
