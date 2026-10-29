package com.example.demo.catalog.repository;

import com.example.demo.catalog.entity.Brand;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
    Optional<Brand> findByCode(String code);
}
