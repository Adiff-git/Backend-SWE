package com.example.demo.user.entity;
import com.example.demo.user.enums.AccountStatus;
import com.example.demo.user.enums.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.PrePersist;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="users") @Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(length=100) private String displayName;
    @Column(nullable=false,length=50) private String username;
    @Column(nullable=false,columnDefinition="TEXT") private String password;
    @Column(length=254) private String email;
    @Column(length=20) private String phoneNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private AccountStatus accountStatus;
    @Enumerated(EnumType.STRING) @Column(length=6) private Gender gender;
    private LocalDate dateOfBirth;
    @Column(nullable=false,updatable=false) private Instant joinedAt;
    @jakarta.persistence.ManyToMany(fetch=jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinTable(name="user_roles",joinColumns=@jakarta.persistence.JoinColumn(name="user_id"),inverseJoinColumns=@jakarta.persistence.JoinColumn(name="role_id"))
    private java.util.Set<Role> roles=new java.util.LinkedHashSet<>();
    @PrePersist protected void assignJoinedAt() {
        if (joinedAt == null) joinedAt=Instant.now();
    }
}
