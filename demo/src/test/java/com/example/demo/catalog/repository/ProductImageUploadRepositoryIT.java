package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.CatalogFixtures.ProductFixture;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductImageUpload;
import com.example.demo.catalog.entity.ProductStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductImageUploadRepositoryIT {

	@Autowired private EntityManager entityManager;
	@Autowired private TransactionTemplate transactions;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ProductRepository products;
	@Autowired private ProductImageRepository images;
	@Autowired private ProductImageUploadRepository uploads;

	@BeforeEach
	@AfterEach
	void clearCommittedRows() {
		jdbc.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, products, categories CASCADE");
	}

	@Test
	void claimPrepared_shouldRequireOwnerLiveSessionAndPreparedState() {
		Rows rows = createRows(false);
		UUID token = UUID.randomUUID();
		String key = durableKey(rows.uploadId());
		assertThat(claim(rows, "other-admin", token, key, 120)).isZero();
		assertThat(claim(rows, rows.preparer(), token, key, 0)).isZero();
		assertThat(claim(rows, rows.preparer(), token, key, 120)).isEqualTo(1);
		assertThat(claim(rows, rows.preparer(), UUID.randomUUID(), durableKey(rows.uploadId()), 120))
				.isZero();
		assertThat(state(rows)).isEqualTo("VERIFIED");
		assertThat(jdbc.queryForObject("SELECT durable_key FROM product_image_uploads WHERE id = ?",
				String.class, rows.uploadId())).isEqualTo(key);
		assertThat(jdbc.queryForObject("SELECT verified_at IS NOT NULL AND claim_expires_at > clock_timestamp() "
				+ "FROM product_image_uploads WHERE id = ?", Boolean.class, rows.uploadId())).isTrue();

		Rows expired = createRows(false);
		jdbc.update("UPDATE product_image_uploads SET expires_at = clock_timestamp() "
				+ "WHERE id = ?", expired.uploadId());
		assertThat(claim(expired, expired.preparer(), UUID.randomUUID(), durableKey(expired.uploadId()), 120))
				.isZero();
		assertThat(state(expired)).isEqualTo("PREPARED");
	}

	@Test
	void renewAndReclaim_shouldFenceOldTokensAndRespectBothDeadlines() {
		Rows rows = createRows(false);
		UUID first = UUID.randomUUID();
		assertThat(claim(rows, rows.preparer(), first, durableKey(rows.uploadId()), 120)).isEqualTo(1);
		assertThat((int) transactions.execute(tx -> uploads.renewClaim(rows.uploadId(), rows.productId(),
				UUID.randomUUID(), 120))).isZero();
		assertThat((int) transactions.execute(tx -> uploads.renewClaim(rows.uploadId(), rows.productId(),
				first, 120))).isEqualTo(1);
		assertThat((int) transactions.execute(tx -> uploads.reclaimExpiredClaim(rows.uploadId(), rows.productId(),
				rows.preparer(), first, UUID.randomUUID(), durableKey(rows.uploadId()), 120))).isZero();

		jdbc.update("UPDATE product_image_uploads SET claim_expires_at = clock_timestamp() - INTERVAL '1 second' "
				+ "WHERE id = ?", rows.uploadId());
		assertThat((int) transactions.execute(tx -> uploads.renewClaim(rows.uploadId(), rows.productId(),
				first, 120))).isZero();
		assertThat((int) transactions.execute(tx -> uploads.reclaimExpiredClaim(rows.uploadId(), rows.productId(),
				"other-admin", first, UUID.randomUUID(), durableKey(rows.uploadId()), 120))).isZero();
		assertThat((int) transactions.execute(tx -> uploads.reclaimExpiredClaim(rows.uploadId(), rows.productId(),
				rows.preparer(), UUID.randomUUID(), UUID.randomUUID(), durableKey(rows.uploadId()), 120)))
				.isZero();
		UUID second = UUID.randomUUID();
		String secondKey = durableKey(rows.uploadId());
		assertThat((int) transactions.execute(tx -> uploads.reclaimExpiredClaim(rows.uploadId(), rows.productId(),
				rows.preparer(), first, second, secondKey, 120))).isEqualTo(1);
		assertThat((int) transactions.execute(tx -> uploads.renewClaim(rows.uploadId(), rows.productId(),
				first, 120))).isZero();
		assertThat(jdbc.queryForObject("SELECT durable_key FROM product_image_uploads WHERE id = ?",
				String.class, rows.uploadId())).isEqualTo(secondKey);

		jdbc.update("UPDATE product_image_uploads SET expires_at = clock_timestamp() "
				+ "WHERE id = ?", rows.uploadId());
		assertThat((int) transactions.execute(tx -> uploads.renewClaim(rows.uploadId(), rows.productId(),
				second, 120))).isZero();
	}

	@Test
	void publishIfEligible_shouldUseOneServerSampleAndReturnZeroForInvalidFences() {
		Rows rows = createRows(true);
		UUID token = UUID.randomUUID();
		String key = durableKey(rows.uploadId());
		assertThat(claim(rows, rows.preparer(), token, key, 120)).isEqualTo(1);
		assertThat(publish(rows, UUID.randomUUID(), key, rows.imageId())).isZero();
		assertThat(publish(rows, token, durableKey(rows.uploadId()), rows.imageId())).isZero();
		assertThat((int) transactions.execute(tx -> uploads.publishIfEligible(rows.uploadId(), UUID.randomUUID(),
				token, key, rows.imageId()))).isZero();
		assertThat(publish(rows, token, key, rows.imageId())).isEqualTo(1);
		assertThat(publish(rows, token, key, rows.imageId())).isZero();
		assertThat(state(rows)).isEqualTo("PUBLISHED");
		assertThat(jdbc.queryForObject("SELECT published_at < expires_at AND claim_token IS NULL "
				+ "AND claim_expires_at IS NULL FROM product_image_uploads WHERE id = ?",
				Boolean.class, rows.uploadId())).isTrue();
		assertThat(jdbc.queryForObject("SELECT product_image_id FROM product_image_uploads WHERE id = ?",
				UUID.class, rows.uploadId())).isEqualTo(rows.imageId());
	}

	@Test
	void publishIfEligible_shouldRejectExpiredSessionLeaseAndInvalidState() {
		Rows expiredSession = createRows(true);
		UUID sessionToken = UUID.randomUUID();
		String sessionKey = durableKey(expiredSession.uploadId());
		assertThat(claim(expiredSession, expiredSession.preparer(), sessionToken, sessionKey, 120))
				.isEqualTo(1);
		jdbc.update("UPDATE product_image_uploads SET expires_at = clock_timestamp() "
				+ "WHERE id = ?", expiredSession.uploadId());
		assertThat(publish(expiredSession, sessionToken, sessionKey, expiredSession.imageId())).isZero();

		Rows expiredLease = createRows(true);
		UUID leaseToken = UUID.randomUUID();
		String leaseKey = durableKey(expiredLease.uploadId());
		assertThat(claim(expiredLease, expiredLease.preparer(), leaseToken, leaseKey, 120)).isEqualTo(1);
		jdbc.update("UPDATE product_image_uploads SET claim_expires_at = clock_timestamp() "
				+ "WHERE id = ?", expiredLease.uploadId());
		assertThat(publish(expiredLease, leaseToken, leaseKey, expiredLease.imageId())).isZero();

		Rows prepared = createRows(true);
		assertThat(publish(prepared, UUID.randomUUID(), durableKey(prepared.uploadId()),
				prepared.imageId())).isZero();
		assertThat(state(prepared)).isEqualTo("PREPARED");
	}

	@Test
	void publishIfEligible_shouldPermitPhysicalCommitAfterPreExpiryValidation() throws InterruptedException {
		Rows rows = createRows(true);
		UUID token = UUID.randomUUID();
		String key = durableKey(rows.uploadId());
		assertThat(claim(rows, rows.preparer(), token, key, 120)).isEqualTo(1);
		jdbc.update("UPDATE product_image_uploads SET expires_at = clock_timestamp() + INTERVAL '2 seconds' "
				+ "WHERE id = ?", rows.uploadId());
		transactions.executeWithoutResult(tx -> {
			assertThat(products.lockIdForNoKeyUpdate(rows.productId())).contains(rows.productId());
			assertThat(uploads.lockByIdAndProductId(rows.uploadId(), rows.productId())).isPresent();
			assertThat(uploads.publishIfEligible(rows.uploadId(), rows.productId(), token,
					key, rows.imageId())).isEqualTo(1);
			try {
				Thread.sleep(2300);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new AssertionError(interrupted);
			}
		});
		assertThat(jdbc.queryForObject("SELECT published_at < expires_at AND clock_timestamp() > expires_at "
				+ "FROM product_image_uploads WHERE id = ?", Boolean.class, rows.uploadId())).isTrue();
	}

	@Test
	void publishIfEligible_shouldRollBackChangesWhenCallerAbortsTransaction() {
		Rows rows = createRows(true);
		UUID token = UUID.randomUUID();
		String key = durableKey(rows.uploadId());
		assertThat(claim(rows, rows.preparer(), token, key, 120)).isEqualTo(1);
		transactions.executeWithoutResult(tx -> {
			assertThat(products.lockIdForNoKeyUpdate(rows.productId())).contains(rows.productId());
			assertThat(uploads.lockByIdAndProductId(rows.uploadId(), rows.productId())).isPresent();
			assertThat(uploads.publishIfEligible(rows.uploadId(), rows.productId(), token,
					key, rows.imageId())).isEqualTo(1);
			tx.setRollbackOnly();
		});
		assertThat(state(rows)).isEqualTo("VERIFIED");
		assertThat(jdbc.queryForObject("SELECT product_image_id FROM product_image_uploads WHERE id = ?",
				UUID.class, rows.uploadId())).isNull();
	}

	@Test
	void expireAbandoned_shouldLeaveLiveClaimAndPublishedResultUntouched() {
		Rows rows = createRows(true);
		UUID token = UUID.randomUUID();
		String key = durableKey(rows.uploadId());
		assertThat(claim(rows, rows.preparer(), token, key, 120)).isEqualTo(1);
		jdbc.update("UPDATE product_image_uploads SET expires_at = clock_timestamp() "
				+ "WHERE id = ?", rows.uploadId());
		assertThat((int) transactions.execute(tx -> uploads.expireAbandoned(rows.uploadId(), rows.productId())))
				.isZero();
		jdbc.update("UPDATE product_image_uploads SET claim_expires_at = clock_timestamp() "
				+ "WHERE id = ?", rows.uploadId());
		assertThat((int) transactions.execute(tx -> uploads.expireAbandoned(rows.uploadId(), rows.productId())))
				.isEqualTo(1);
		assertThat(state(rows)).isEqualTo("EXPIRED");
		assertThat((int) transactions.execute(tx -> uploads.expireAbandoned(rows.uploadId(), rows.productId())))
				.isZero();

		Rows published = createRows(true);
		UUID publishedToken = UUID.randomUUID();
		String publishedKey = durableKey(published.uploadId());
		assertThat(claim(published, published.preparer(), publishedToken, publishedKey, 120)).isEqualTo(1);
		assertThat(publish(published, publishedToken, publishedKey, published.imageId())).isEqualTo(1);
		jdbc.update("UPDATE product_image_uploads SET expires_at = published_at + INTERVAL '1 second' "
				+ "WHERE id = ?", published.uploadId());
		assertThat((int) transactions.execute(tx -> uploads.expireAbandoned(published.uploadId(),
				published.productId()))).isZero();
		assertThat(state(published)).isEqualTo("PUBLISHED");
	}

	@Test
	void expireAbandoned_shouldRemainRetryableAfterTransactionRollback() {
		Rows rows = createRows(false);
		jdbc.update("UPDATE product_image_uploads SET expires_at = clock_timestamp() "
				+ "WHERE id = ?", rows.uploadId());
		transactions.executeWithoutResult(tx -> {
			assertThat(uploads.expireAbandoned(rows.uploadId(), rows.productId())).isEqualTo(1);
			tx.setRollbackOnly();
		});
		assertThat(state(rows)).isEqualTo("PREPARED");
		assertThat((int) transactions.execute(tx -> uploads.expireAbandoned(rows.uploadId(), rows.productId())))
				.isEqualTo(1);
		assertThat(state(rows)).isEqualTo("EXPIRED");
	}

	@Test
	void expireAbandoned_shouldWaitForPublicationAndKeepCommittedResult() throws Exception {
		Rows rows = createRows(true);
		UUID token = UUID.randomUUID();
		String key = durableKey(rows.uploadId());
		assertThat(claim(rows, rows.preparer(), token, key, 120)).isEqualTo(1);
		jdbc.update("UPDATE product_image_uploads SET expires_at = clock_timestamp() + INTERVAL '2 seconds' "
				+ "WHERE id = ?", rows.uploadId());
		CountDownLatch publishedInsideTransaction = new CountDownLatch(1);
		CountDownLatch releasePublication = new CountDownLatch(1);
		CountDownLatch cleanupStarted = new CountDownLatch(1);
		try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
			CompletableFuture<Integer> publication = CompletableFuture.supplyAsync(() -> transactions.execute(tx -> {
				products.lockIdForNoKeyUpdate(rows.productId()).orElseThrow();
				uploads.lockByIdAndProductId(rows.uploadId(), rows.productId()).orElseThrow();
				int updated = uploads.publishIfEligible(rows.uploadId(), rows.productId(), token,
						key, rows.imageId());
				publishedInsideTransaction.countDown();
				await(releasePublication);
				return updated;
			}), workers);
			assertThat(publishedInsideTransaction.await(5, TimeUnit.SECONDS)).isTrue();
			CompletableFuture<Integer> cleanup = CompletableFuture.supplyAsync(() -> transactions.execute(tx -> {
				cleanupStarted.countDown();
				return uploads.expireAbandoned(rows.uploadId(), rows.productId());
			}), workers);
			try {
				assertThat(cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue();
				assertThat(cleanup.isDone()).isFalse();
				Thread.sleep(2300);
			} finally {
				releasePublication.countDown();
			}
			assertThat(publication.get(5, TimeUnit.SECONDS)).isEqualTo(1);
			assertThat(cleanup.get(5, TimeUnit.SECONDS)).isZero();
		}
		assertThat(state(rows)).isEqualTo("PUBLISHED");
		assertThat(jdbc.queryForObject("SELECT product_image_id FROM product_image_uploads WHERE id = ?",
				UUID.class, rows.uploadId())).isEqualTo(rows.imageId());
	}

	@Test
	void failTerminalUpdates_shouldFenceStateAndClaimToken() {
		Rows prepared = createRows(false);
		assertThat((int) transactions.execute(tx -> uploads.failPrepared(prepared.uploadId(), prepared.productId())))
				.isEqualTo(1);
		assertThat(state(prepared)).isEqualTo("FAILED");
		assertThat((int) transactions.execute(tx -> uploads.failPrepared(prepared.uploadId(), prepared.productId())))
				.isZero();

		Rows claimed = createRows(false);
		UUID token = UUID.randomUUID();
		assertThat(claim(claimed, claimed.preparer(), token, durableKey(claimed.uploadId()), 120))
				.isEqualTo(1);
		assertThat((int) transactions.execute(tx -> uploads.failClaimOwned(claimed.uploadId(),
				claimed.productId(), UUID.randomUUID()))).isZero();
		assertThat((int) transactions.execute(tx -> uploads.failClaimOwned(claimed.uploadId(),
				claimed.productId(), token))).isEqualTo(1);
		assertThat(state(claimed)).isEqualTo("FAILED");
		assertThat(jdbc.queryForObject("SELECT failed_at IS NOT NULL FROM product_image_uploads WHERE id = ?",
				Boolean.class, claimed.uploadId())).isTrue();
	}

	@Test
	void imageQueries_shouldExposeCountOrderAndPrimaryWithoutChangingGallery() {
		Rows rows = createRows(true);
		assertThat(images.countByProduct_Id(rows.productId())).isEqualTo(1);
		assertThat(images.existsByProduct_IdAndDisplayOrder(rows.productId(), 0)).isTrue();
		assertThat(images.existsByProduct_IdAndDisplayOrder(rows.productId(), 1)).isFalse();
		assertThat(images.findMaxDisplayOrder(rows.productId())).contains(0);
		assertThat(images.findByProduct_IdAndPrimaryTrue(rows.productId())).isPresent();
	}

	@Test
	void publishIfEligible_shouldSerializeConcurrentCompletionClaims() throws Exception {
		Rows rows = createRows(true);
		UUID token = UUID.randomUUID();
		String key = durableKey(rows.uploadId());
		assertThat(claim(rows, rows.preparer(), token, key, 120)).isEqualTo(1);
		CountDownLatch firstUpdated = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch secondStarted = new CountDownLatch(1);
		try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
			CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> transactions.execute(tx -> {
				products.lockIdForNoKeyUpdate(rows.productId()).orElseThrow();
				uploads.lockByIdAndProductId(rows.uploadId(), rows.productId()).orElseThrow();
				int updated = uploads.publishIfEligible(rows.uploadId(), rows.productId(), token,
						key, rows.imageId());
				firstUpdated.countDown();
				await(releaseFirst);
				return updated;
			}), workers);
			assertThat(firstUpdated.await(5, TimeUnit.SECONDS)).isTrue();
			CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> transactions.execute(tx -> {
				secondStarted.countDown();
				products.lockIdForNoKeyUpdate(rows.productId()).orElseThrow();
				uploads.lockByIdAndProductId(rows.uploadId(), rows.productId()).orElseThrow();
				return uploads.publishIfEligible(rows.uploadId(), rows.productId(), token,
						key, rows.imageId());
			}), workers);
			try {
				assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
				assertThat(second.isDone()).isFalse();
			} finally {
				releaseFirst.countDown();
			}
			assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(1);
			assertThat(second.get(5, TimeUnit.SECONDS)).isZero();
		}
		assertThat(state(rows)).isEqualTo("PUBLISHED");
	}

	private Rows createRows(boolean withImage) {
		return transactions.execute(tx -> {
			ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, ProductStatus.DRAFT);
			ProductImageUpload upload = CatalogFixtures.upload(fixture.product());
			entityManager.persist(upload);
			ProductImage image = null;
			if (withImage) {
				image = CatalogFixtures.image(fixture.product(), 0, true);
				entityManager.persist(image);
			}
			entityManager.flush();
			return new Rows(fixture.product().getId(), upload.getId(), upload.getPreparerSubject(),
					image == null ? null : image.getId());
		});
	}

	private int claim(Rows rows, String preparer, UUID token, String key, int leaseSeconds) {
		return transactions.execute(tx -> uploads.claimPrepared(rows.uploadId(), rows.productId(),
				preparer, token, key, leaseSeconds));
	}

	private int publish(Rows rows, UUID token, String key, UUID imageId) {
		return transactions.execute(tx -> {
			products.lockIdForNoKeyUpdate(rows.productId()).orElseThrow();
			uploads.lockByIdAndProductId(rows.uploadId(), rows.productId()).orElseThrow();
			return uploads.publishIfEligible(rows.uploadId(), rows.productId(), token, key, imageId);
		});
	}

	private String state(Rows rows) {
		return jdbc.queryForObject("SELECT state FROM product_image_uploads WHERE id = ?",
				String.class, rows.uploadId());
	}

	private String durableKey(UUID uploadId) {
		return "final/" + uploadId + "/" + UUID.randomUUID();
	}

	private void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new AssertionError(interrupted);
		}
	}

	private record Rows(UUID productId, UUID uploadId, String preparer, UUID imageId) {
	}
}
