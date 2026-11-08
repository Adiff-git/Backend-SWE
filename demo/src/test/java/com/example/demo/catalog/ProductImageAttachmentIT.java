package com.example.demo.catalog;

import com.example.demo.catalog.exception.CatalogConflictException;
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
            Callable<ProductImageService.Outcome> action = () -> {
                gate.await(); return attachments.attach(product, upload.getId(), actor, "Front", null, null);
            };
            var first = pool.submit(action); var second = pool.submit(action); gate.countDown();
            var a = first.get(60, TimeUnit.SECONDS); var b = second.get(60, TimeUnit.SECONDS);
            assertThat(a.image().imageId()).isEqualTo(b.image().imageId());
            assertThat(a.replay()).isNotEqualTo(b.replay());
        }
        assertThat(db.queryForObject("SELECT count(*) FROM product_images WHERE product_id=?", Integer.class, product)).isEqualTo(1);
        assertThat(attachments.attach(product, upload.getId(), actor, "Front", null, null).replay()).isTrue();
        // Shared upload remains usable by the avatar flow after attachment.
        assertThat(media.validateImageForAttachment(actor, upload.getId()).getId()).isEqualTo(upload.getId());
        assertThatThrownBy(() -> attachments.attach(product, upload.getId(), actor, "Changed", null, null))
                .isInstanceOf(CatalogConflictException.class);
    }

    @Test void ownershipCompletionAndContentDeduplicationAreEnforced() {
        Long actor = owner(); UUID product = product();
        var upload = prepare(actor);
        assertThatThrownBy(() -> attachments.attach(product, upload.getId(), actor, "Front", null, null))
                .isInstanceOf(UploadStateConflictException.class);
        stage(upload); media.completeImageUpload(actor, upload.getId());
        assertThatThrownBy(() -> attachments.attach(product, upload.getId(), actor + 1000, "Front", null, null))
                .isInstanceOf(ImageUploadNotFoundException.class);
        attachments.attach(product, upload.getId(), actor, "Front", null, null);
        var duplicate = prepare(actor); stage(duplicate); media.completeImageUpload(actor, duplicate.getId());
        assertThatThrownBy(() -> attachments.attach(product, duplicate.getId(), actor, "Duplicate", null, null))
                .isInstanceOf(CatalogConflictException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM product_images WHERE product_id=?", Integer.class, product)).isEqualTo(1);
    }
}
