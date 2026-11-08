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
@Table(name = "recent_auth_proofs")
@Getter
@Setter
@NoArgsConstructor
public class RecentAuthProof {
    @Id
    private UUID id;
    @Column(columnDefinition = "BYTEA", updatable=false)
    private byte[] secretHash;
    @Column(updatable=false)
    private Long ownerUserId;
    @Column(updatable=false)
    private UUID initiatingSessionId;
    @Enumerated(EnumType.STRING)
    @Column(length = 16, updatable=false)
    private Method method;
    @Enumerated(EnumType.STRING)
    @Column(length = 32, updatable=false)
    private Operation operation;
    @Column(updatable=false)
    private Long providerIdentityId;
    @Column(updatable=false)
    private Instant authenticatedAt;
    @Column(updatable=false)
    private Instant expiresAt;
    private Instant consumedAt;

    public enum Method { PASSWORD, GOOGLE }

    public enum Operation { EMAIL_CHANGE, INITIAL_PASSWORD, CONNECT_GOOGLE }
}
