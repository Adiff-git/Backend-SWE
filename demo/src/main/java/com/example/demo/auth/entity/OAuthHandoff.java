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
@Table(name = "oauth_handoffs")
@Getter
@Setter
@NoArgsConstructor
public class OAuthHandoff {
    @Id
    private UUID id;
    @Column(columnDefinition = "BYTEA")
    private byte[] codeHash;
    @Column(updatable=false)
    private UUID attemptId;
    @Enumerated(EnumType.STRING)
    @Column(length = 16, updatable=false)
    private Purpose purpose;
    @Enumerated(EnumType.STRING)
    @Column(length = 32, updatable=false)
    private Operation operation;
    @Column(columnDefinition = "TEXT", updatable=false)
    private String validatedIssuer;
    @Column(columnDefinition = "TEXT", updatable=false)
    private String subject;
    @Column(length = 254, updatable=false)
    private String providerEmail;
    @Column(updatable=false)
    private boolean providerEmailVerified;
    @Column(length = 100, updatable=false)
    private String initialDisplayName;
    @Column(updatable=false)
    private Long ownerUserId;
    @Column(updatable=false)
    private UUID initiatingSessionId;
    @Column(updatable=false)
    private UUID recentAuthProofId;
    @Column(updatable=false)
    private UUID browserSlotId;
    @Column(updatable=false)
    private int browserGeneration;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(length = 43, updatable=false)
    private String pkceChallenge;
    @Column(updatable=false)
    private Instant authenticatedAt;
    @Column(updatable=false)
    private Instant createdAt;
    @Column(updatable=false)
    private Instant expiresAt;
    private Instant consumedAt;

    public enum Purpose { LOGIN, LINK, REAUTH }

    public enum Operation { EMAIL_CHANGE, INITIAL_PASSWORD, CONNECT_GOOGLE }
}
