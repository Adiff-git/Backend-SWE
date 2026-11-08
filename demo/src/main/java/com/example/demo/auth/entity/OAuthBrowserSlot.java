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

@Entity
@Table(name = "oauth_browser_slots")
@Getter
@Setter
@NoArgsConstructor
public class OAuthBrowserSlot {
    @Id
    private UUID id;
    @Column(columnDefinition = "BYTEA")
    private byte[] slotSecretHash;
    private int generation;
    private UUID currentAttemptId;
    private Instant expiresAt;
}
