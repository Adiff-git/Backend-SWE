package com.example.demo.catalog.service;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.config.SqlCapture;

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
public class CatalogQueryCountIT {

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
                entityManager.persist(CatalogFixtures.image(fixture.product(), 1, false));
                entityManager.persist(CatalogFixtures.image(fixture.product(), 2, false));
                fixture.variant().setOptionCombination(Map.of("choice", "base"));
                for (int option = 1; option <= 19; option++) {
                    entityManager.persist(CatalogFixtures.variant(fixture.product(), Map.of("choice", "extra-" + option),
                            BigDecimal.valueOf(5000 + option)));
                }
			}
			entityManager.flush();
		});
		SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
		sessionFactory.getStatistics().clear();
        for (int size : new int[]{20, 100}) {
            sessionFactory.getStatistics().clear();
            SqlCapture.begin();
            ProductPage page;
            java.util.List<String> statements;
            try {
                page = service.listProducts(0, size);
            } finally {
                statements = SqlCapture.end();
            }
            assertThat(page.getItems()).hasSize(size);
            assertThat(page.getTotalItems()).isEqualTo(101);
            assertThat(sessionFactory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(6);
            assertThat(statements).hasSizeLessThanOrEqualTo(6);
            assertThat(statements).anyMatch(sql -> sql.contains("selected_products AS MATERIALIZED"));
            recordSql("listing-" + size, statements);
            var criteria=new com.example.demo.catalog.dto.request.ProductSearchRequest();criteria.setQ("product");criteria.setMinPrice(java.math.BigInteger.ZERO);criteria.setSize(size);
            SqlCapture.begin();
            ProductPage searched;
            java.util.List<String> searchSql;
            try {searched=service.listProducts(criteria);} finally {searchSql=SqlCapture.end();}
            assertThat(searched.getItems()).hasSize(size);assertThat(searched.getTotalItems()).isEqualTo(101);
            long setStatements=searchSql.stream().filter(sql->sql.contains("set_config")).count();
            assertThat(setStatements).isEqualTo(1);assertThat(searchSql.size()-setStatements).isLessThanOrEqualTo(8);
            recordSql("search-"+size,searchSql);

        }
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

    @Test
    void jdbcCapture_shouldObserveDirectJdbcOutsideHibernate() {
        recordEnvironment();
        SqlCapture.begin();
        java.util.List<String> statements;
        try {
            assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isOne();
            jdbc.execute("SELECT 2");
        } finally {
            statements = SqlCapture.end();
        }
        assertThat(statements).containsExactly("SELECT 1", "SELECT 2");
        recordSql("jdbc-calibration", statements);
    }

    private void recordEnvironment() {
        var environment = new java.util.LinkedHashMap<String, Object>();
        environment.put("database", jdbc.queryForMap("SELECT current_database() AS database,current_schema() AS schema,version() AS version,"
                + "current_setting('server_encoding') AS encoding,current_setting('default_transaction_isolation') AS default_isolation"));
        environment.put("extensions", jdbc.queryForList("SELECT extname,extversion FROM pg_extension ORDER BY extname"));
        environment.put("collation", jdbc.queryForList("SELECT collname,collisdeterministic,collversion FROM pg_collation WHERE collname='und-x-icu'"));
        environment.put("history", jdbc.queryForList("SELECT installed_rank,version,checksum,success FROM flyway_schema_history ORDER BY installed_rank"));
        environment.put("jdbcUrl", jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection -> connection.getMetaData().getURL()));
        environment.put("serialExecution", true);
        environment.put("storage", "test-local signer;no AWS network calls");
        writeEvidence("runtime-environment", environment);
    }

    private void recordSql(String scenario, java.util.List<String> statements) {
        writeEvidence(scenario, Map.of("scenario", scenario, "dataStatements", statements.size(), "sql", statements));
    }

    private void writeEvidence(String name, Object evidence) {
        try {
            var output = java.nio.file.Path.of("target", "t019-evidence");
            java.nio.file.Files.createDirectories(output);
            java.nio.file.Files.writeString(output.resolve(name + ".json"),
                    tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(evidence));
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

	@TestConfiguration(proxyBeanMethods = false)
    @Import(com.example.demo.config.SqlCaptureConfiguration.class)
	public static class FakeStorageConfiguration {
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
