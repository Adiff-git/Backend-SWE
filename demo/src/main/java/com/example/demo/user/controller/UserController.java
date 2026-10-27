package com.example.demo.user.controller;
import com.example.demo.user.service.UserService;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.user.dto.request.UpdateUserRequest;
import com.example.demo.user.dto.response.PrivateUserResponse;
import com.example.demo.user.dto.response.PublicUserResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import lombok.RequiredArgsConstructor;
@RestController @RequiredArgsConstructor
public class UserController {
    private final UserService users;
    private final CurrentUserIdProvider identity;
    @GetMapping("/api/me") public PrivateUserResponse getCurrentUser(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return users.getCurrentUser(identity.getCurrentUserId());
    }
    @PutMapping("/api/me") public PrivateUserResponse update(@Valid @RequestBody UpdateUserRequest request,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return users.updateUser(identity.getCurrentUserId(),request);
    }
    @GetMapping("/api/users/{id}") public PublicUserResponse getUser(@PathVariable Long id,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return users.getUser(id);
    }
}
