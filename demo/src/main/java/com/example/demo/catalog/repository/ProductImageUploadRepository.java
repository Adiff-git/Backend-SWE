package com.example.demo.catalog.repository;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.demo.catalog.entity.ProductImageUpload;

public interface ProductImageUploadRepository extends JpaRepository<ProductImageUpload, UUID> {

	Optional<ProductImageUpload> findByIdAndProduct_Id(UUID id, UUID productId);

	/** Call only after locking the owning Product row in the service transaction. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT u FROM ProductImageUpload u WHERE u.id = :id AND u.product.id = :productId")
	Optional<ProductImageUpload> lockByIdAndProductId(@Param("id") UUID id,
			@Param("productId") UUID productId);

	/** Called in a short service transaction after the preparer and staged bytes are verified. */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			WITH sampled AS MATERIALIZED (SELECT clock_timestamp() AS checked_at)
			UPDATE product_image_uploads AS u
			SET state = 'VERIFIED', claim_token = :newToken,
			    claim_expires_at = sampled.checked_at + :leaseSeconds * INTERVAL '1 second',
			    durable_key = :durableKey, verified_at = sampled.checked_at
			FROM sampled
			WHERE u.id = :uploadId AND u.product_id = :productId
			  AND u.preparer_subject = :preparerSubject AND u.state = 'PREPARED'
			  AND sampled.checked_at < u.expires_at AND :leaseSeconds > 0
			""", nativeQuery = true)
	int claimPrepared(@Param("uploadId") UUID uploadId, @Param("productId") UUID productId,
			@Param("preparerSubject") String preparerSubject, @Param("newToken") UUID newToken,
			@Param("durableKey") String durableKey, @Param("leaseSeconds") int leaseSeconds);

	/** A current token may renew a live lease without extending the upload session. */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			WITH sampled AS MATERIALIZED (SELECT clock_timestamp() AS checked_at)
			UPDATE product_image_uploads AS u
			SET claim_expires_at = sampled.checked_at + :leaseSeconds * INTERVAL '1 second'
			FROM sampled
			WHERE u.id = :uploadId AND u.product_id = :productId
			  AND u.state = 'VERIFIED' AND u.claim_token = :claimToken
			  AND sampled.checked_at < u.expires_at
			  AND sampled.checked_at < u.claim_expires_at AND :leaseSeconds > 0
			""", nativeQuery = true)
	int renewClaim(@Param("uploadId") UUID uploadId, @Param("productId") UUID productId,
			@Param("claimToken") UUID claimToken, @Param("leaseSeconds") int leaseSeconds);

	/** Same preparer may replace an expired claim; the observed token fences races. */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			WITH sampled AS MATERIALIZED (SELECT clock_timestamp() AS checked_at)
			UPDATE product_image_uploads AS u
			SET claim_token = :newToken,
			    claim_expires_at = sampled.checked_at + :leaseSeconds * INTERVAL '1 second',
			    durable_key = :newDurableKey, verified_at = sampled.checked_at
			FROM sampled
			WHERE u.id = :uploadId AND u.product_id = :productId
			  AND u.preparer_subject = :preparerSubject AND u.state = 'VERIFIED'
			  AND u.claim_token = :observedToken
			  AND u.claim_expires_at <= sampled.checked_at
			  AND sampled.checked_at < u.expires_at AND :leaseSeconds > 0
			""", nativeQuery = true)
	int reclaimExpiredClaim(@Param("uploadId") UUID uploadId, @Param("productId") UUID productId,
			@Param("preparerSubject") String preparerSubject,
			@Param("observedToken") UUID observedToken, @Param("newToken") UUID newToken,
			@Param("newDurableKey") String newDurableKey, @Param("leaseSeconds") int leaseSeconds);

	/** Final business write: caller must hold Product then upload locks and roll back on zero rows. */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			WITH sampled AS MATERIALIZED (SELECT clock_timestamp() AS checked_at)
			UPDATE product_image_uploads AS u
			SET state = 'PUBLISHED', product_image_id = :imageId,
			    published_at = sampled.checked_at, claim_token = NULL, claim_expires_at = NULL
			FROM sampled
			WHERE u.id = :uploadId AND u.product_id = :productId
			  AND u.state = 'VERIFIED' AND u.claim_token = :claimToken
			  AND u.durable_key = :durableKey AND u.product_image_id IS NULL
			  AND sampled.checked_at < u.expires_at
			  AND sampled.checked_at < u.claim_expires_at
			""", nativeQuery = true)
	int publishIfEligible(@Param("uploadId") UUID uploadId, @Param("productId") UUID productId,
			@Param("claimToken") UUID claimToken, @Param("durableKey") String durableKey,
			@Param("imageId") UUID imageId);

	/** Cleanup may expire only a session with no live publication claim. */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			WITH sampled AS MATERIALIZED (SELECT clock_timestamp() AS checked_at)
			UPDATE product_image_uploads AS u
			SET state = 'EXPIRED', claim_token = NULL, claim_expires_at = NULL
			FROM sampled
			WHERE u.id = :uploadId AND u.product_id = :productId
			  AND u.state IN ('PREPARED', 'VERIFIED')
			  AND u.expires_at <= sampled.checked_at
			  AND (u.claim_expires_at IS NULL OR u.claim_expires_at <= sampled.checked_at)
			""", nativeQuery = true)
	int expireAbandoned(@Param("uploadId") UUID uploadId, @Param("productId") UUID productId);

	@Modifying(flushAutomatically = true)
	@Query(value = """
			WITH sampled AS MATERIALIZED (SELECT clock_timestamp() AS checked_at)
			UPDATE product_image_uploads AS u
			SET state = 'FAILED', failed_at = sampled.checked_at
			FROM sampled
			WHERE u.id = :uploadId AND u.product_id = :productId
			  AND u.state = 'PREPARED' AND u.claim_token IS NULL
			  AND u.claim_expires_at IS NULL
			""", nativeQuery = true)
	int failPrepared(@Param("uploadId") UUID uploadId, @Param("productId") UUID productId);

	@Modifying(flushAutomatically = true)
	@Query(value = """
			WITH sampled AS MATERIALIZED (SELECT clock_timestamp() AS checked_at)
			UPDATE product_image_uploads AS u
			SET state = 'FAILED', failed_at = sampled.checked_at,
			    claim_token = NULL, claim_expires_at = NULL
			FROM sampled
			WHERE u.id = :uploadId AND u.product_id = :productId
			  AND u.state = 'VERIFIED' AND u.claim_token = :claimToken
			  AND sampled.checked_at < u.claim_expires_at
			""", nativeQuery = true)
	int failClaimOwned(@Param("uploadId") UUID uploadId, @Param("productId") UUID productId,
			@Param("claimToken") UUID claimToken);
}
