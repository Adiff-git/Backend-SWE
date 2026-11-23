package com.example.demo.catalog;

import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.service.ProductImageService;
import com.example.demo.media.integration.SeededMediaTestSupport;
import com.example.demo.media.exception.ImageUploadNotFoundException;
import com.example.demo.media.exception.UploadStateConflictException;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class ProductImageAttachmentIT extends SeededMediaTestSupport {
    @Autowired ProductImageService attachments;
    @Autowired TransactionTemplate transactions;
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate db;

    private UUID product() {
        return transactions.execute(s -> CatalogFixtures.persistProduct(entities,
                com.example.demo.catalog.entity.ProductStatus.DRAFT).product().getId());
    }

    @Test void sharedUploadAttachesOnceEvenWhenRequestsRace() throws Exception {
        Long actor = owner(); UUID product = product();
        var upload = prepare(actor); stage(upload); media.completeImageUpload(actor, upload.getId());
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            long version=currentVersion(product),taxonomy=currentTaxonomy();
            Callable<Object> action = () -> {
                gate.await();
                try { return attachments.attach(product,upload.getId(),actor,"Front",null,null,version,taxonomy); }
                catch(AdminCatalogException conflict){ return conflict; }
            };
            var first=pool.submit(action);var second=pool.submit(action);gate.countDown();
            var results=java.util.List.of(first.get(60,TimeUnit.SECONDS),second.get(60,TimeUnit.SECONDS));
            assertThat(results.stream().filter(ProductImageService.Outcome.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(AdminCatalogException.class::isInstance)).singleElement().satisfies(value ->
                    assertThat(((AdminCatalogException)value).getCode()).isEqualTo("CATALOG_STALE_VERSION"));

        }
        assertThat(db.queryForObject("SELECT count(*) FROM product_images WHERE product_id=?", Integer.class, product)).isEqualTo(1);
        assertThat(attachCurrent(product,upload.getId(),actor,"Front").replay()).isTrue();
        // Shared upload remains usable by the avatar flow after attachment.
        assertThat(media.validateImageForAttachment(actor, upload.getId()).getId()).isEqualTo(upload.getId());
        assertThatThrownBy(() -> attachCurrent(product,upload.getId(),actor,"Changed"))
                .isInstanceOf(AdminCatalogException.class);
    }

    @Test void ownershipCompletionAndContentDeduplicationAreEnforced() {
        Long actor = owner(); UUID product = product();
        var upload = prepare(actor);
        assertThatThrownBy(() -> attachCurrent(product,upload.getId(),actor,"Front"))
                .isInstanceOf(UploadStateConflictException.class);
        stage(upload); media.completeImageUpload(actor, upload.getId());
        assertThatThrownBy(() -> attachCurrent(product,upload.getId(),actor+1000,"Front"))
                .isInstanceOf(ImageUploadNotFoundException.class);
        attachCurrent(product,upload.getId(),actor,"Front");
        var duplicate = prepare(actor); stage(duplicate); media.completeImageUpload(actor, duplicate.getId());
        assertThatThrownBy(() -> attachCurrent(product,duplicate.getId(),actor,"Duplicate"))
                .isInstanceOf(AdminCatalogException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM product_images WHERE product_id=?", Integer.class, product)).isEqualTo(1);
    }
    private long currentVersion(UUID product){return db.queryForObject("SELECT version FROM products WHERE id=?",Long.class,product);}
    private long currentTaxonomy(){return db.queryForObject("SELECT version FROM catalog_taxonomy_state WHERE id=1",Long.class);}
    private ProductImageService.Outcome attachCurrent(UUID product,UUID upload,Long actor,String alt){
        return attachments.attach(product,upload,actor,alt,null,null,currentVersion(product),currentTaxonomy());
    }
}
