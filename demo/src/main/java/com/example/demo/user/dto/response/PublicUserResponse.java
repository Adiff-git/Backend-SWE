package com.example.demo.user.dto.response;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class PublicUserResponse {
    private Long id;
    private String username;
    private String displayName;
    private UUID profileImageId;
    private String profileImageUrl;
    private java.time.Instant joinedAt;
}
