package com.example.demo.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "brands")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;
    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String code;
    @Column(nullable = false)
    private String name;

    public Brand(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
