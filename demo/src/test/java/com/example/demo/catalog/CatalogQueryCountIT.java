package com.example.demo.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.CatalogFixtures.ProductFixture;
import com.example.demo.catalog.dto.response.ProductDetail;
import com.example.demo.catalog.dto.response.ProductPage;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.entity.ProductVariant;
import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(CatalogQueryCountIT.FakeStorageConfiguration.class)
class CatalogQueryCountIT {

	@Autowired private ProductCatalogService service;
	@Autowired private EntityManager entityManager;
	@Autowired private EntityManagerFactory entityManagerFactory;
	@Autowired private TransactionTemplate transactions;
	@Autowired private JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void clearCommittedData() {
		jdbc.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, products, categories CASCADE");
	}

	@Test
	void pageOfOneHundredImagesAndManyRelatedRowsUsesBoundedQueries() {
		transactions.executeWithoutResult(status -> {
			for (int index = 0; index < 101; index++) {
				ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
				entityManager.persist(CatalogFixtures.image(fixture.product(), 0, true));
			}
			entityManager.flush();
		});
		SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
		sessionFactory.getStatistics().clear();
		ProductPage page = service.listProducts(0, 100);
		assertThat(page.getItems()).hasSize(100);
		assertThat(page.getTotalItems()).isEqualTo(101);
		assertThat(sessionFactory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(6);
	}

	@Test
	void detailWithSeveralVariantsAndMaximumGalleryUsesBoundedQueries() {
		UUID id = transactions.execute(status -> {
			ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
			fixture.variant().setOptionCombination(Map.of("choice", "first"));
			for (int index = 1; index < 5; index++) {
				ProductVariant variant = CatalogFixtures.variant(fixture.product(),
						Map.of("choice", "choice-" + index), BigDecimal.valueOf(index * 1000L));
				entityManager.persist(variant);
			}
			for (int index = 0; index < 10; index++) {
				ProductImage image = CatalogFixtures.image(fixture.product(), index, index == 0);
				entityManager.persist(image);
			}
			entityManager.flush();
			return fixture.product().getId();
		});
		SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
		sessionFactory.getStatistics().clear();
		ProductDetail detail = service.getProduct(id);
		assertThat(detail.getVariants()).hasSize(5);
		assertThat(detail.getImages()).hasSize(10);
		assertThat(sessionFactory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(5);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeStorageConfiguration {
		@Bean
		@Primary
		ProductImageStorage queryCountStorage() {
			return () -> new PreparedReadSigner() {
				@Override
				public String signReadUrl(String privateObjectKey) {
					return "https://images.test/read?X-Amz-Expires=300";
				}

				@Override
				public void close() {
				}
			};
		}
	}
}
