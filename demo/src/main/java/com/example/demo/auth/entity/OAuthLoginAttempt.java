package com.example.demo.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Table(name = "oauth_login_attempts")
@Getter
@Setter
@NoArgsConstructor
public class OAuthLoginAttempt {
    @Id
    private UUID id;
    @Column(columnDefinition = "BYTEA")
    private byte[] startTicketHash;
    @Enumerated(EnumType.STRING)
    @Column(length = 16, updatable=false)
    private Purpose purpose;
    @Enumerated(EnumType.STRING)
    @Column(length = 32, updatable=false)
    private Operation operation;
    @Column(updatable=false)
    private Long ownerUserId;
    @Column(updatable=false)
    private UUID initiatingSessionId;
    @Column(updatable=false)
    private UUID recentAuthProofId;
    @Column(length = 32, updatable=false)
    private String destinationKey;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(length = 43, updatable=false)
    private String pkceChallenge;
    @Column(updatable=false)
    private UUID browserSlotId;
    @Column(updatable=false)
    private int browserGeneration;
    @Column(columnDefinition = "BYTEA", updatable=false)
    private byte[] browserBindingHash;
    @Column(columnDefinition = "BYTEA")
    private byte[] oauthStateHash;
    @Column(columnDefinition = "BYTEA")
    private byte[] encryptedRequest;
    @Column(length = 32)
    private String encryptionKeyId;
    @Column(columnDefinition = "BYTEA")
    private byte[] nonce;
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Status status;
    @Column(updatable=false)
    private Instant createdAt;
    @Column(updatable=false)
    private Instant expiresAt;
    private Instant consumedAt;

    public enum Purpose { LOGIN, LINK, REAUTH }

    public enum Operation { EMAIL_CHANGE, INITIAL_PASSWORD, CONNECT_GOOGLE }

    public enum Status { CREATED, STARTED, CALLBACK_VALIDATED, FAILED, EXPIRED }
}
