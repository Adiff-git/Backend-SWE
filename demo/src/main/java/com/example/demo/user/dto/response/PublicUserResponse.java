package com.example.demo.user.dto.response;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class PublicUserResponse {
    private Long id;
    private String username;
    private String displayName;
    private Long profileImageId;
    private String profileImageUrl;
    private java.time.Instant joinedAt;
}
