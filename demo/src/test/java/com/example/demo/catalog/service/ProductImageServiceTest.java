package com.example.demo.catalog.service;

import com.example.demo.catalog.exception.*;
import com.example.demo.catalog.storage.ProductImageCheckpoint;
import com.example.demo.catalog.validator.ProductImageContentValidator;
import com.example.demo.media.entity.ImageUpload;
import com.example.demo.media.exception.ImageUploadNotFoundException;
import com.example.demo.media.exception.UploadStateConflictException;
import com.example.demo.media.repository.ImageUploadRepository;
import com.example.demo.media.testdata.MediaTestData;
import com.example.demo.storage.dto.response.StoredObjectContent;
import com.example.demo.storage.dto.response.StoredObjectMetadata;
import com.example.demo.storage.service.ObjectStorageService;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductImageServiceTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final ImageUploadRepository uploads = mock(ImageUploadRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final ProductImageCheckpoint checkpoint = mock(ProductImageCheckpoint.class);
    private final ProductImageService service = new ProductImageService(db, transactions, uploads, storage, checkpoint,
            new ProductImageContentValidator());
    private final UUID product = UUID.randomUUID(), source = UUID.randomUUID();
    private final ImageUpload upload = ImageUpload.pending(8L, "staging/key", "image/png", Instant.now(), Instant.now().plusSeconds(900));

    @BeforeEach void setup() {
        when(uploads.findByIdAndOwnerUserId(source, 8L)).thenReturn(Optional.of(upload));
        when(db.queryForObject(anyString(), eq(Integer.class), eq(product))).thenReturn(1);
        when(transactions.execute(any())).thenAnswer(i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(null));
    }

    @Test void rejectsAnotherOwnersImageAndUncompletedUploadBeforeStorage() {
        when(uploads.findByIdAndOwnerUserId(source, 9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.attach(product, source, 9L, "Front", null, null)).isInstanceOf(ImageUploadNotFoundException.class);
        assertThatThrownBy(() -> service.attach(product, source, 8L, "Front", null, null)).isInstanceOf(UploadStateConflictException.class);
        upload.expire();
        assertThatThrownBy(() -> service.attach(product, source, 8L, "Front", null, null)).isInstanceOf(UploadStateConflictException.class);
        verifyNoInteractions(storage, checkpoint, transactions);
    }

    @Test void replaySkipsStorageButRejectsChangedMetadata() {
        upload.complete("images/users/8/key");
        UUID image = UUID.randomUUID();
        when(db.queryForList(anyString(), eq(product), eq(source))).thenReturn(List.of(Map.of(
                "id", image, "alt_text", "Front", "width", 2, "height", 2, "display_order", 0, "is_primary", true)));
        var result = service.attach(product, source, 8L, "Front", null, null);
        assertThat(result.replay()).isTrue();
        assertThat(result.image().imageId()).isEqualTo(image);
        assertThatThrownBy(() -> service.attach(product, source, 8L, "Changed", null, null)).isInstanceOf(CatalogConflictException.class);
        verifyNoInteractions(storage, checkpoint, transactions);
    }

    @Test void rejectsOversizeSourceBeforeReadingIt() {
        upload.complete("images/users/8/key");
        when(storage.inspect(upload.getFinalKey())).thenReturn(Optional.of(new StoredObjectMetadata("etag", 10_485_761, "image/png")));
        assertThatThrownBy(() -> service.attach(product, source, 8L, "Front", null, null)).isInstanceOf(InvalidProductImageException.class);
        verify(storage, never()).readBoundedIfMatch(any(), any(), anyLong());
        verifyNoInteractions(checkpoint, transactions);
    }

    private byte[] validSource() throws Exception {
        upload.complete("images/users/8/key");
        byte[] bytes = MediaTestData.fixture("valid.png");
        when(storage.inspect(anyString())).thenReturn(Optional.of(new StoredObjectMetadata("etag", bytes.length, "image/png")));
        when(storage.readBoundedIfMatch(anyString(), eq("etag"), anyLong())).thenAnswer(i -> {
            var path = Files.createTempFile("product-image-test", ".png"); Files.write(path, bytes);
            return new StoredObjectContent(path, new StoredObjectMetadata("etag", bytes.length, "image/png"));
        });
        when(db.queryForList("SELECT id FROM products WHERE id=? FOR UPDATE", product)).thenReturn(List.of(Map.of("id", product)));
        return bytes;
    }

    @Test void checkpointsVerifiedCopyBeforePublishingAndNeverDeletesSharedSource() throws Exception {
        byte[] bytes = validSource();
        var result = service.attach(product, source, 8L, "Front", null, null);
        assertThat(result.replay()).isFalse();
        assertThat(result.image().isPrimary()).isTrue();
        assertThat(result.image().displayOrder()).isZero();
        var sequence = inOrder(checkpoint, transactions);
        sequence.verify(checkpoint).save(startsWith("images/products/" + product + "/" + source + "/"), eq("image/png"), eq(bytes));
        sequence.verify(transactions).execute(any());
        verify(storage, never()).deleteStaging(any());
    }

    @Test void checkpointFailureCannotPublishGallery() throws Exception {
        validSource();
        doThrow(new CatalogStorageUnavailableException()).when(checkpoint).save(any(), any(), any());
        assertThatThrownBy(() -> service.attach(product, source, 8L, "Front", null, null)).isInstanceOf(CatalogStorageUnavailableException.class);
        verifyNoInteractions(transactions);
    }

    @Test void corruptCopyCannotCreateCheckpointOrGallery() throws Exception {
        validSource();
        when(storage.readBoundedIfMatch(startsWith("images/products/"), eq("etag"), anyLong())).thenAnswer(i -> {
            var path = Files.createTempFile("corrupt-product-copy", ".png"); Files.write(path, new byte[]{1, 2, 3});
            return new StoredObjectContent(path, new StoredObjectMetadata("etag", 3, "image/png"));
        });
        assertThatThrownBy(() -> service.attach(product, source, 8L, "Front", null, null)).isInstanceOf(InvalidProductImageException.class);
        verifyNoInteractions(checkpoint, transactions);
    }

    @Test void orderAndPrimaryConstraintsCannotAlterGallery() throws Exception {
        validSource();
        assertThatThrownBy(() -> service.attach(product, source, 8L, "Front", false, null)).isInstanceOf(CatalogConflictException.class);
        when(db.queryForList("SELECT * FROM product_images WHERE product_id=? ORDER BY display_order", product))
                .thenReturn(List.of(Map.of("object_key", "different-content", "display_order", 0)));
        assertThatThrownBy(() -> service.attach(product, source, 8L, "Front", true, 0)).isInstanceOf(CatalogConflictException.class);
        verify(db, never()).update(startsWith("UPDATE product_images"), eq(product));
    }

    @Test void galleryLimitAndDuplicateContentPreventInsertion() throws Exception {
        byte[] bytes = validSource();
        String sha = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        String query = "SELECT * FROM product_images WHERE product_id=? ORDER BY display_order";
        when(db.queryForList(query, product)).thenReturn(List.of(Map.of("object_key", "images/other/" + sha)));
        assertThatThrownBy(() -> service.attach(product, source, 8L, "Front", null, null)).isInstanceOf(CatalogConflictException.class);
        when(db.queryForList(query, product)).thenReturn(Collections.nCopies(10, Map.of("object_key", "other")));
        assertThatThrownBy(() -> service.attach(product, source, 8L, "Front", null, null)).isInstanceOf(CatalogConflictException.class);
        verify(db, never()).update(startsWith("INSERT INTO product_images"), any(Object[].class));
    }
}
