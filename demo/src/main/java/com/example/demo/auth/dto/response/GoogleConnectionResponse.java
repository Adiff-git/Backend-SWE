package com.example.demo.auth.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class GoogleConnectionResponse {
    private final boolean connected;
    private final Instant linkedAt;
}
