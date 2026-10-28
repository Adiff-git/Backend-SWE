package com.example.demo.auth.entity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="refresh_credentials") @Getter @Setter @NoArgsConstructor
public class RefreshCredential {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="session_id",nullable=false,updatable=false) private UUID sessionId;
    @jakarta.persistence.ManyToOne(fetch=jakarta.persistence.FetchType.LAZY,optional=false)
    @jakarta.persistence.JoinColumn(name="session_id",insertable=false,updatable=false)
    @lombok.Setter(lombok.AccessLevel.NONE) private AuthenticationSession session;
    @Column(nullable=false,updatable=false) private byte[] tokenHash;
    @Column(nullable=false,updatable=false) private Instant issuedAt;
    private Instant consumedAt;
}
