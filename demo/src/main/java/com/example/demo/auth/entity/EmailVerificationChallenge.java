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

@Entity @Table(name="email_verification_challenges") @Getter @Setter @NoArgsConstructor
public class EmailVerificationChallenge {
    @Id private UUID id;
    private Long userId;
    private UUID pendingRegistrationId;
    private int generation;
    @Column(length=254) private String destination;
    @Enumerated(EnumType.STRING) @Column(length=16) private Purpose purpose;
    @Column(columnDefinition="BYTEA") private byte[] codeMac;
    @Column(length=32) private String keyId;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean active;
    private Instant consumedAt;
    private Instant supersededAt;
    @Enumerated(EnumType.STRING) @Column(length=16) private DeliveryStatus deliveryStatus;
    public enum Purpose { REGISTRATION,EMAIL_CHANGE }
    public enum DeliveryStatus { QUEUED,SENT,FAILED,EXPIRED }
}
