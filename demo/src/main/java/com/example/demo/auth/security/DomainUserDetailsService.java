package com.example.demo.auth.security;
import com.example.demo.user.service.UserService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class DomainUserDetailsService implements UserDetailsService {
    private final UserService users;
    public UserDetails loadUserByUsername(String identifier) {
        Long id=users.resolveSecurityIdentifier(identifier).orElseThrow(()->new UsernameNotFoundException("Authentication failed"));
        com.example.demo.user.dto.response.UserSecuritySnapshot snapshot=users.getUserSecuritySnapshot(id);
        if(snapshot.getEncodedPassword()==null)throw new UsernameNotFoundException("Authentication failed");
        return new CustomUserDetails(snapshot);
    }
}
