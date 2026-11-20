package com.example.demo.catalog.service;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.storage.ProductImageStorage;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest @ActiveProfiles("test")
class CatalogOrderReadServiceIT {
    @Autowired CatalogOrderReadService service;
    @Autowired EntityManager entities;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ProductImageStorage storage;

    @AfterEach void cleanup() { jdbc.execute("TRUNCATE TABLE categories CASCADE"); }

    @Test void getPrimaryImageUrls_shouldRemainUsableAfterStorageFailureAndHideUnpublished() {
        UUID id = transactions.execute(s -> {
            CatalogFixtures.ProductFixture fixture = CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED);
            entities.persist(CatalogFixtures.image(fixture.product(), 0, true));
            return fixture.product().getId();
        });
        when(storage.prepareReadSigner()).thenThrow(new CatalogStorageUnavailableException());
        assertThat(service.getPrimaryImageUrls(List.of(id))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=?", Long.class, id)).isEqualTo(1);
        ProductImageStorage.PreparedReadSigner signer = mock(ProductImageStorage.PreparedReadSigner.class);
        org.mockito.Mockito.doReturn(signer).when(storage).prepareReadSigner();
        when(signer.signReadUrl(org.mockito.ArgumentMatchers.anyString())).thenReturn("https://signed/test");
        assertThat(service.getPrimaryImageUrls(List.of(id))).containsKey(id);
        jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=?", id);
        assertThat(service.getPrimaryImageUrls(List.of(id))).isEmpty();
        transactions.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM product_images WHERE product_id=?", id);
            jdbc.update("DELETE FROM product_variants WHERE product_id=?", id);
            jdbc.update("DELETE FROM products WHERE id=?", id);
        });
        assertThat(service.getPrimaryImageUrls(List.of(id))).isEmpty();
    }

    @Test void getPrimaryImageUrls_shouldRecheckPublicationAfterCompetingWriter() {
        UUID id = transactions.execute(s -> {
            CatalogFixtures.ProductFixture fixture = CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED);
            entities.persist(CatalogFixtures.image(fixture.product(), 0, true));
            return fixture.product().getId();
        });
        ProductImageStorage.PreparedReadSigner signer = mock(ProductImageStorage.PreparedReadSigner.class);
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            when(storage.prepareReadSigner()).thenAnswer(call -> {
                // Candidate keys have been read, but publication locks have not yet been acquired.
                worker.submit(() -> jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=?", id))
                        .get(5, TimeUnit.SECONDS);
                return signer;
            });
            assertThat(service.getPrimaryImageUrls(List.of(id))).isEmpty();
            org.mockito.Mockito.verify(signer, org.mockito.Mockito.never()).signReadUrl(org.mockito.ArgumentMatchers.anyString());
        }
    }
}
