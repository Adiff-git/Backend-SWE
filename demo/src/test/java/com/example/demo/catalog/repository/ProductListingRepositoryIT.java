package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.CatalogFixtures.ProductFixture;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
class ProductListingRepositoryIT {

	@Autowired private ProductRepository products;
	@Autowired private EntityManager entityManager;
	@Autowired private TransactionTemplate transactions;
	@Autowired private JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void clearCommittedData() {
		jdbc.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, products, categories CASCADE");
	}

	@Test
	void pagesOnlyPublishedIdsInStableAscendingOrderAndCountsOnlyThem() {
		UUID one = persist(ProductStatus.PUBLISHED);
		persist(ProductStatus.DRAFT);
		UUID two = persist(ProductStatus.PUBLISHED);
		persist(ProductStatus.ARCHIVED);
		UUID three = persist(ProductStatus.PUBLISHED);
		List<UUID> expected = List.of(one, two, three).stream()
				.sorted(Comparator.comparing(UUID::toString)).toList();
		Page<UUID> first = products.findPublishedIdPage(PageRequest.of(0, 2));
		Page<UUID> second = products.findPublishedIdPage(PageRequest.of(1, 2));
		Page<UUID> beyond = products.findPublishedIdPage(PageRequest.of(9, 2));
		assertThat(first.getContent()).containsExactlyElementsOf(expected.subList(0, 2));
		assertThat(second.getContent()).containsExactly(expected.get(2));
		assertThat(first.getTotalElements()).isEqualTo(3);
		assertThat(second.getTotalPages()).isEqualTo(2);
		assertThat(beyond.getContent()).isEmpty();
		assertThat(beyond.getTotalElements()).isEqualTo(3);
		assertThat(products.findPublishedIdPage(PageRequest.of(0, 2)).getContent())
				.containsExactlyElementsOf(first.getContent());
	}

	@Test
	void emptyPublishedSetReturnsZeroTotals() {
		persist(ProductStatus.DRAFT);
		persist(ProductStatus.ARCHIVED);
		Page<UUID> page = products.findPublishedIdPage(PageRequest.of(0, 20));
		assertThat(page.getContent()).isEmpty();
		assertThat(page.getTotalElements()).isZero();
		assertThat(page.getTotalPages()).isZero();
	}

	private UUID persist(ProductStatus status) {
		return transactions.execute(transaction -> {
			ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, status);
			entityManager.flush();
			return fixture.product().getId();
		});
	}
}
