package com.example.demo.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name="pending_registrations") @Getter @Setter @NoArgsConstructor
public class PendingRegistration {
    @Id private UUID id;
    @Column(length=254) private String destination;
    @Column(columnDefinition="BYTEA") private byte[] credentialHash;
    private int generation;
    @Enumerated(EnumType.STRING) @Column(length=16) private State state;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant consumedAt;
    private Instant expiredAt;
    public enum State { ACTIVE,CONFIRMED,CONFLICT,EXPIRED }
}
