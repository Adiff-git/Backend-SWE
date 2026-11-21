package com.example.demo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.user.entity.User;
import com.example.demo.user.enums.AccountStatus;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.service.impl.UserReviewReadServiceImpl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class UserReviewReadServiceImplTest {
    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"INACTIVE", "SUSPENDED", "BANNED"})
    void getReviewAuthors_shouldAnonymizeInactiveIdentity(AccountStatus status) {
        UserRepository repository = mock(UserRepository.class);
        User user = new User(); user.setId(1L); user.setUsername("private-name");
        user.setDisplayName("Private display"); user.setProfileImageId(UUID.randomUUID()); user.setAccountStatus(status);
        when(repository.findAllById(List.of(1L))).thenReturn(List.of(user));
        com.example.demo.user.dto.response.ReviewAuthorSummary result = new UserReviewReadServiceImpl(repository).getReviewAuthors(List.of(1L)).get(1L);
        assertThat(result.getDisplayName()).isEqualTo("Former customer");
        assertThat(result.getProfileImage()).isNull();
    }
}
