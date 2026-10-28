package com.example.demo.user.service;

import com.example.demo.media.exception.ImageUploadNotFoundException;
import com.example.demo.media.service.MediaService;
import com.example.demo.user.converter.UserConverter;
import com.example.demo.user.dto.request.UpdateUserRequest;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.service.impl.UserServiceImpl;
import com.example.demo.user.validator.UserValidator;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class UserImageServiceTest {
    @Test void update_shouldRejectForeignImageBeforeChangingOtherFields() {
        UserRepository repository = mock(UserRepository.class);
        MediaService media = mock(MediaService.class);
        User user = new User();
        user.setId(1L);
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(user));
        when(media.validateImageForAttachment(1L, 2L)).thenThrow(new ImageUploadNotFoundException());
        UserServiceImpl service = new UserServiceImpl(repository, new UserConverter(), new UserValidator(), mock(EntityManager.class), 5000, media);
        UpdateUserRequest request = JsonMapper.builder().build().readValue("{\"displayName\":\"Rejected\",\"profileImageId\":2}", UpdateUserRequest.class);
        assertThatThrownBy(() -> service.updateUser(1L, request)).isInstanceOf(ImageUploadNotFoundException.class);
        assertThat(user.getDisplayName()).isNull();
    }

    @Test void update_shouldClearReferenceWithoutCallingStorage() {
        UserRepository repository = mock(UserRepository.class);
        MediaService media = mock(MediaService.class);
        User user = new User();
        user.setProfileImageId(2L);
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(user));
        UserServiceImpl service = new UserServiceImpl(repository, new UserConverter(), new UserValidator(), mock(EntityManager.class), 5000, media);
        service.updateUser(1L, JsonMapper.builder().build().readValue("{\"profileImageId\":null}", UpdateUserRequest.class));
        assertThat(user.getProfileImageId()).isNull();
        verifyNoInteractions(media);
    }
}
