package com.example.demo.auth.service;
import com.example.demo.auth.security.DomainUserDetailsService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class EmailLoginServiceTest extends AuthCommandTestSupport {
    @Test void execute_shouldVerifyCurrentIdentifierSnapshotOwnsCredentialsAndAuthorities() {
        when(users.resolveSecurityIdentifier("Alice@example.com")).thenReturn(java.util.Optional.of(1L));
        var details=new DomainUserDetailsService(users).loadUserByUsername("Alice@example.com");
        assertThat(details.getUsername()).isEqualTo("Alice");
        assertThat(details.getPassword()).isEqualTo("encoded");
        when(users.resolveSecurityIdentifier("old@example.com")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(()->new DomainUserDetailsService(users).loadUserByUsername("old@example.com")).isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    }
}
