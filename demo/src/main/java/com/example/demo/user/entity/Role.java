package com.example.demo.user.entity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="roles") @Getter @Setter @NoArgsConstructor
public class Role {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=50) private String name;
    @jakarta.persistence.ManyToMany(fetch=jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinTable(name="role_permissions",joinColumns=@jakarta.persistence.JoinColumn(name="role_id"),inverseJoinColumns=@jakarta.persistence.JoinColumn(name="permission_id"))
    private java.util.Set<Permission> permissions=new java.util.LinkedHashSet<>();
}
