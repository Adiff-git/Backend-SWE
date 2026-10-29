package com.example.demo.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "catalog_source_links")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogSourceLink {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;
    @Column(nullable = false) private String source;
    @Column(name = "source_key", nullable = false) private String sourceKey;
    @Column(name = "source_url", nullable = false) private String sourceUrl;
    @Column(name = "source_record_id") private String sourceRecordId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;
    @Column(name = "source_payload_sha256", nullable = false) private String sourcePayloadSha256;
    @Column(name = "normalized_content_sha256", nullable = false) private String normalizedContentSha256;
    @Column(name = "normalization_version", nullable = false) private String normalizationVersion;
    @Column(name = "approved_manifest_sha256", nullable = false) private String approvedManifestSha256;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
