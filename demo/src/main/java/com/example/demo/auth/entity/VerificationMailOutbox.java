package com.example.demo.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name="verification_mail_outbox") @Getter @Setter @NoArgsConstructor
public class VerificationMailOutbox {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private UUID challengeId;
    @Column(columnDefinition="BYTEA") private byte[] encryptedPayload;
    @Column(columnDefinition="BYTEA") private byte[] nonce;
    @Column(length=32) private String keyId;
    @Enumerated(EnumType.STRING) @Column(length=16) private State state;
    private int attemptCount;
    private Instant nextAttemptAt;
    private Instant leaseUntil;
    private Instant expiresAt;
    public enum State { PENDING,LEASED,SENT,FAILED,EXPIRED }
}
