package com.example.demo.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name="google_identities") @Getter @Setter @NoArgsConstructor
public class GoogleIdentity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(updatable=false) private Long userId;
    @Column(length=16,updatable=false) private String provider;
    @Column(columnDefinition="TEXT",updatable=false) private String issuer;
    @Column(columnDefinition="TEXT",updatable=false) private String subject;
    @Column(length=254) private String providerEmail;
    @Column(updatable=false) private Instant linkedAt;
    private Instant lastSuccessfulLoginAt;
}
