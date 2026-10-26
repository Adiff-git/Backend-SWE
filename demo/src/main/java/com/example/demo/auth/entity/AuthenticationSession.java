package com.example.demo.auth.entity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="authentication_sessions") @Getter @Setter @NoArgsConstructor
public class AuthenticationSession {
    @Id private UUID id;
    @Column(name="user_id",nullable=false,updatable=false) private Long userId;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    @Column(nullable=false,updatable=false) private Instant expiresAt;
    @jakarta.persistence.ManyToOne(fetch=jakarta.persistence.FetchType.LAZY,optional=false)
    @jakarta.persistence.JoinColumn(name="user_id",insertable=false,updatable=false)
    @lombok.Setter(lombok.AccessLevel.NONE) private com.example.demo.user.entity.User user;
    private Instant revokedAt;
    @Column(length=32) private String revocationReason;
    public boolean isActiveAt(Instant now) {
        return revokedAt==null && now.isBefore(expiresAt);
    }
    public void revoke(Instant now,String reason) {
        if(revokedAt==null) {
            revokedAt=now;
            revocationReason=reason;
        }
    }
}
