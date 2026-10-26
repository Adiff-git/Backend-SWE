package com.example.demo.user.service;
import com.example.demo.user.service.impl.UserServiceImpl;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.validator.UserValidator;
import com.example.demo.user.converter.UserConverter;
import com.example.demo.user.dto.request.UpdateUserRequest;
import com.example.demo.user.entity.User;
import com.example.demo.user.testdata.UserTestData;
import com.example.demo.user.exception.IdentityConflictException;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class UserServiceImplTest {
    private final UserRepository repository=mock(UserRepository.class);
    private final UserServiceImpl service=new UserServiceImpl(repository,new UserConverter(),new UserValidator(),mock(EntityManager.class),5000);
    @Test void updateUser_shouldPreserveOmittedFieldsAndImmutableValues() {
        User u=UserTestData.user("Alice");
        u.setId(8L);
        u.setEmail("old@example.com");
        when(repository.findForUpdate(8L)).thenReturn(Optional.of(u));
        UpdateUserRequest request=new UpdateUserRequest();
        request.setDisplayName(" Updated ");
        service.updateUser(8L,request);
        assertThat(u.getDisplayName()).isEqualTo("Updated");
        assertThat(u.getEmail()).isEqualTo("old@example.com");
        assertThat(u.getUsername()).isEqualTo("Alice");
        assertThat(u.getJoinedAt()).isEqualTo(java.time.Instant.parse("2026-10-26T00:00:00Z"));
        verify(repository).setLockTimeout("5000ms");
    }
    @Test void updateUser_shouldRejectConflictBeforeMutation() {
        User u=UserTestData.user("Alice");
        when(repository.findForUpdate(8L)).thenReturn(Optional.of(u));
        when(repository.usernameExists("Bob",8L)).thenReturn(true);
        UpdateUserRequest request=new UpdateUserRequest();
        request.setUsername("Bob");
        assertThatThrownBy(()->service.updateUser(8L,request)).isInstanceOf(IdentityConflictException.class);
        assertThat(u.getUsername()).isEqualTo("Alice");
        verify(repository,never()).flush();
    }
}
