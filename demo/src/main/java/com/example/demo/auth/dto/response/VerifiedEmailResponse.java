package com.example.demo.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class VerifiedEmailResponse {
    private final String email;
    private final boolean emailVerified;
}
