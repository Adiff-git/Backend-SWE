package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.CatalogFixtures.ProductFixture;
import com.example.demo.catalog.dto.request.ProductSearchRequest.SpecificationGroup;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.entity.ProductVariant;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.catalog.search.ProductCandidatePage;
import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.ProductSearchNormalizer;
import com.example.demo.catalog.search.ProductSearchSort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Execution(ExecutionMode.SAME_THREAD)
class ProductSearchBaselineRepositoryIT {
    @Autowired private ProductRepository products;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationContext context;

    private static final Path OBSERVATIONS = Path.of("target", "t016-evidence", "observations.jsonl");

    @BeforeAll
    static void resetObservations() throws IOException {
        Files.createDirectories(OBSERVATIONS.getParent());
        Files.deleteIfExists(OBSERVATIONS);
    }

    @BeforeEach
    @AfterEach
    void cleanFixtures() {
        if (TestTransaction.isActive()) {
            // D7 owns a rollback-only,deferred exceptional fixture;do not TRUNCATE pending events.
            return;
        }
        jdbc.execute("TRUNCATE TABLE product_image_uploads,product_images,catalog_source_links,wishlist_items,product_variants,products,brands,categories CASCADE");
    }

    @Test
    void legacyMembershipOrderAndCountsUseOneStatementPerPage() {
        UUID first = persist(ProductStatus.PUBLISHED);
        UUID second = persist(ProductStatus.PUBLISHED);
        UUID third = persist(ProductStatus.PUBLISHED);
        persist(ProductStatus.DRAFT);
        persist(ProductStatus.ARCHIVED);
        List<UUID> expected = List.of(first, second, third).stream()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        ProductCandidatePage one = page(0, 2);
        ProductCandidatePage two = page(1, 2);
        assertThat(one.getItems()).extracting(ProductCandidatePage.Candidate::getProductId)
                .containsExactlyElementsOf(expected.subList(0, 2));
        assertThat(two.getItems()).extracting(ProductCandidatePage.Candidate::getProductId).containsExactly(expected.get(2));
        assertThat(one.getTotalElements()).isEqualTo(3);
        assertThat(two.getTotalElements()).isEqualTo(3);
        assertThat(products.findPublishedIdPage(PageRequest.of(0, 2)).getContent())
                .containsExactlyElementsOf(expected.subList(0, 2));
        assertThat(page(0, 2).getItems()).isEqualTo(one.getItems());
        assertThatThrownBy(() -> one.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void selectsCheapestVariantWithUuidTieAndPreservesExactAmount() {
        List<UUID> cheapestIds = new ArrayList<>();
        ProductFixture fixture = transactions.execute(status -> {
            ProductFixture product = CatalogFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
            product.variant().setOptionCombination(Map.of("choice", "expensive"));
            product.variant().setSellingPrice(new BigDecimal("9999999999999999999999999999999999999999"));
            ProductVariant one = CatalogFixtures.variant(product.product(), Map.of("choice", "one"), new BigDecimal("123456789012345678901234567890"));
            ProductVariant two = CatalogFixtures.variant(product.product(), Map.of("choice", "two"), new BigDecimal("123456789012345678901234567890"));
            entityManager.persist(one);
            entityManager.persist(two);
            cheapestIds.add(one.getId());
            cheapestIds.add(two.getId());
            entityManager.flush();
            return product;
        });
        UUID expectedVariant = cheapestIds.stream().min(Comparator.comparing(UUID::toString)).orElseThrow();
        ProductCandidatePage result = page(0, 20);
        assertThat(result.getTotalElements()).isOne();
        assertThat(result.getItems()).hasSize(1);
        ProductCandidatePage.Candidate candidate = result.getItems().getFirst();
        assertThat(candidate.getProductId()).isEqualTo(fixture.product().getId());
        assertThat(candidate.getRepresentativeVariantId()).isEqualTo(expectedVariant);
        assertThat(candidate.getDisplayAmount()).isEqualTo(new BigInteger("123456789012345678901234567890"));
    }

    @Test
    void emptyAndBeyondLastPagesKeepAccurateTotalsIncludingMaxOffset() {
        persist(ProductStatus.DRAFT);
        assertThat(page(0, 20).getItems()).isEmpty();
        assertThat(page(0, 20).getTotalElements()).isZero();
        persist(ProductStatus.PUBLISHED);
        assertThat(page(8, 20).getItems()).isEmpty();
        assertThat(page(8, 20).getTotalElements()).isOne();
        assertThat(page(Integer.MAX_VALUE, 100).getItems()).isEmpty();
        assertThat(page(Integer.MAX_VALUE, 100).getTotalElements()).isOne();
    }

    @Test
    @Transactional
    void deferredOrphanFixtureRetainsLegacyMembershipWithoutWeakeningConstraints() {
        ProductFixture fixture = CatalogFixtures.publishedProduct();
        entityManager.persist(fixture.category());
        entityManager.persist(fixture.product());
        entityManager.flush();
        ProductCandidatePage result = page(0, 20);
        assertThat(result.getTotalElements()).isOne();
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().getProductId()).isEqualTo(fixture.product().getId());
        assertThat(result.getItems().getFirst().getRepresentativeVariantId()).isNull();
        assertThat(result.getItems().getFirst().getDisplayAmount()).isNull();
        assertThat(jdbc.queryForObject("SELECT tgenabled::text FROM pg_trigger WHERE tgname='ct_products_variant_integrity'", String.class)).isEqualTo("O");
        // The deliberately exceptional fixture is rolled back,not committed and no trigger is disabled.
    }

    @Test
    void rejectsInvalidCriteriaBeforeExecutingSql() {
        List<ProductSearchCriteria> unsupported = new ArrayList<>();
        unsupported.add(ProductSearchCriteria.builder().specificationGroups(List.of(new SpecificationGroup())).build());
        unsupported.add(ProductSearchCriteria.builder().page(-1).build());
        unsupported.add(ProductSearchCriteria.builder().size(0).build());
        unsupported.add(ProductSearchCriteria.builder().size(101).build());
        SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
        factory.getStatistics().clear();
        for (ProductSearchCriteria criteria : unsupported) {
            assertThatThrownBy(() -> products.findPublishedCandidates(criteria)).isInstanceOf(InvalidCatalogRequestException.class);
        }
        assertThatThrownBy(() -> products.findPublishedCandidates(null)).isInstanceOf(InvalidCatalogRequestException.class);
        assertThat(factory.getStatistics().getPrepareStatementCount()).isZero();
        assertThat(context.getBeansOfType(SpecificationKeywordContributor.class)).isEmpty();
        observe(Map.of("scenario", "unsupported-criteria", "preparedStatements", factory.getStatistics().getPrepareStatementCount(),
                "rejectedCriteria", unsupported.size() + 1, "activeSpecificationProviders", 0));
    }

    @Test
    void representativeMetadataDoesNotMultiplyWithVariantsOrImages() {
        transactions.executeWithoutResult(status -> {
            ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
            fixture.variant().setSellingPrice(BigDecimal.ZERO);
            entityManager.persist(CatalogFixtures.image(fixture.product(), 0, true));
            entityManager.persist(CatalogFixtures.image(fixture.product(), 1, false));
            entityManager.flush();
        });
        ProductCandidatePage result = page(0, 100);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalElements()).isOne();
        assertThat(result.getItems().getFirst().getRepresentativeVariantId()).isNotNull();
        assertThat(result.getItems().getFirst().getDisplayAmount()).isEqualTo(BigInteger.ZERO);
    }

    private UUID persist(ProductStatus status) {
        return transactions.execute(transaction -> {
            ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, status);
            entityManager.flush();
            return fixture.product().getId();
        });
    }

    private void observe(Map<String, Object> observation) {
        try {
            Files.writeString(OBSERVATIONS, JsonMapper.builder().build().writeValueAsString(observation) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private ProductCandidatePage page(int page, int size) {
        SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
        factory.getStatistics().clear();
        ProductCandidatePage result = products.findPublishedCandidates(ProductSearchCriteria.builder().page(page).size(size).build());
        long statements = factory.getStatistics().getPrepareStatementCount();
        assertThat(statements).isEqualTo(1);
        observe(Map.of("scenario", "legacy-page", "page", page, "size", size,
                "preparedStatements", statements, "totalElements", result.getTotalElements(), "items", result.getItems()));
        return result;
    }
}
