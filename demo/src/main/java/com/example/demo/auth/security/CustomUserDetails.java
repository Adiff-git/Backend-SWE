package com.example.demo.auth.security;
import com.example.demo.user.dto.response.UserSecuritySnapshot;
import com.example.demo.user.enums.AccountStatus;
import java.util.Collection;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
public final class CustomUserDetails implements UserDetails {
    private final UserSecuritySnapshot snapshot;
    public CustomUserDetails(UserSecuritySnapshot snapshot) {
        this.snapshot=snapshot;
    }
    public Long getId() {
        return snapshot.getId();
    }
    public String getUsername() {
        return snapshot.getUsername();
    }
    public String getPassword() {
        return snapshot.getEncodedPassword();
    }
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return snapshot.getAuthorities().stream().map(SimpleGrantedAuthority::new).toList();
    }
    public boolean isEnabled() {
        return snapshot.getAccountStatus()==AccountStatus.ACTIVE;
    }
    public boolean isAccountNonLocked() {
        return snapshot.getAccountStatus()!=AccountStatus.SUSPENDED && snapshot.getAccountStatus()!=AccountStatus.BANNED;
    }
    public boolean isAccountNonExpired() {
        return true;
    }
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
