package com.example.demo.catalog.repository;

import com.example.demo.catalog.entity.CatalogSourceLink;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogSourceLinkRepository extends JpaRepository<CatalogSourceLink, UUID> {
    Optional<CatalogSourceLink> findBySourceAndSourceKey(String source, String sourceKey);
}
