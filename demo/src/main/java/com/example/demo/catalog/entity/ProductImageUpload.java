package com.example.demo.catalog.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_image_uploads")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImageUpload {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Setter(AccessLevel.NONE)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@NotBlank
	@Setter(AccessLevel.NONE)
	@Column(name = "preparer_subject", nullable = false, updatable = false, columnDefinition = "text")
	private String preparerSubject;

	@NotBlank
	@Column(name = "staging_key", nullable = false, columnDefinition = "text")
	private String stagingKey;

	@Column(name = "durable_key", columnDefinition = "text")
	private String durableKey;

	@Column(name = "claim_token")
	private UUID claimToken;

	@Column(name = "claim_expires_at", columnDefinition = "timestamptz")
	private Instant claimExpiresAt;

	@Column(name = "proposed_content_type", nullable = false, columnDefinition = "varchar")
	private String proposedContentType;

	@Column(name = "proposed_byte_size", nullable = false)
	private long proposedByteSize;

	@NotBlank
	@Column(name = "alt_text", nullable = false, columnDefinition = "text")
	private String altText;

	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false, columnDefinition = "varchar")
	private ProductImageUploadState state;

	@Column(name = "prepared_at", nullable = false, columnDefinition = "timestamptz")
	private Instant preparedAt;

	@Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
	private Instant expiresAt;

	@Column(name = "verified_at", columnDefinition = "timestamptz")
	private Instant verifiedAt;

	@Column(name = "published_at", columnDefinition = "timestamptz")
	private Instant publishedAt;

	@Column(name = "failed_at", columnDefinition = "timestamptz")
	private Instant failedAt;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_image_id")
	private ProductImage productImage;

	public ProductImageUpload(String preparerSubject) {
		this.preparerSubject = preparerSubject;
	}
}
