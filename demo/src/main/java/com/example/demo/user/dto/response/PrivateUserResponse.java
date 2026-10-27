package com.example.demo.user.dto.response;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class PrivateUserResponse {
    private Long id;
    private String displayName;
    private String username;
    private String email;
    private String phoneNumber;
    private com.example.demo.user.enums.AccountStatus accountStatus;
    private com.example.demo.user.enums.Gender gender;
    private java.time.LocalDate dateOfBirth;
    private java.time.Instant joinedAt;
    private Long profileImageId;
    private String profileImageUrl;
}
