package com.example.demo.user.service;
import com.example.demo.user.entity.User;
import com.example.demo.user.dto.request.UpdateUserRequest;
import com.example.demo.user.dto.response.PrivateUserResponse;
import com.example.demo.user.dto.response.PublicUserResponse;
import jakarta.validation.Valid;
public interface UserService {
    User getUserEntity(Long id);
    User getUserEntityForUpdate(Long id);
    PrivateUserResponse getCurrentUser(Long actorId);
    PublicUserResponse getUser(Long id);
    PrivateUserResponse updateUser(Long actorId,@Valid UpdateUserRequest request);
}
