package com.example.demo.auth.repository;

import com.example.demo.auth.entity.GoogleIdentity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleIdentityRepository extends JpaRepository<GoogleIdentity,Long> {
    Optional<GoogleIdentity> findByProviderAndIssuerAndSubject(String provider,String issuer,String subject);
    Optional<GoogleIdentity> findByUserIdAndProvider(Long userId,String provider);
}
