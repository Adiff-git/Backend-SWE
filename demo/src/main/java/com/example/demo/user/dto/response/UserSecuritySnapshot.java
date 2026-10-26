package com.example.demo.user.dto.response;
import com.example.demo.user.enums.AccountStatus;
import java.util.Set;
import lombok.Getter;
import lombok.AllArgsConstructor;
/** Sensitive internal snapshot: never serialize as HTTP output or log. */
@Getter @AllArgsConstructor
public class UserSecuritySnapshot {
    private final Long id;
    private final String username;
    private final String encodedPassword;
    private final AccountStatus accountStatus;
    private final Set<String> authorities;
}
