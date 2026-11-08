package com.example.demo.catalog.service;

import com.example.demo.catalog.exception.*;
import com.example.demo.catalog.storage.ProductImageCheckpoint;
import com.example.demo.catalog.validator.ProductImageContentValidator;
import com.example.demo.media.enums.ImageUploadStatus;
import com.example.demo.media.exception.ImageUploadNotFoundException;
import com.example.demo.media.exception.UploadStateConflictException;
import com.example.demo.media.repository.ImageUploadRepository;
import com.example.demo.storage.service.ObjectStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Storage validation/checkpointing precedes a short, serialized gallery transaction. */
@Service
@RequiredArgsConstructor
public class ProductImageService {
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final ImageUploadRepository uploads;
    private final ObjectStorageService storage;
    private final ProductImageCheckpoint checkpoint;
    private final ProductImageContentValidator validator;

    public record Attached(UUID imageId, UUID productId, String altText, int width, int height,
            int displayOrder, boolean isPrimary) {}
    public record Outcome(Attached image, boolean replay) {}

    public Outcome attach(UUID product, UUID source, Long actor, String alt, Boolean primary, Integer order) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Image attachment must not join an ambient transaction");
        if (alt == null || alt.isBlank() || order != null && order < 0)
            throw new InvalidCatalogRequestException("Invalid image attachment");
        // Resolve by owner even for retries: an administrator cannot attach another user's upload.
        var upload = uploads.findByIdAndOwnerUserId(source, actor).orElseThrow(ImageUploadNotFoundException::new);
        if (upload.getStatus() != ImageUploadStatus.COMPLETED) throw new UploadStateConflictException();
        var existing = replay(product, source, alt, primary, order);
        if (existing != null) return existing;
        if (db.queryForObject("SELECT count(*) FROM products WHERE id=?", Integer.class, product) != 1)
            throw new ProductNotFoundException();

        String sourceKey = upload.getFinalKey();
        var metadata = storage.inspect(sourceKey).orElseThrow(CatalogStorageUnavailableException::new);
        if (metadata.getLength() < 1 || metadata.getLength() > ProductImageContentValidator.MAX_BYTES
                || !upload.getContentType().equals(metadata.getContentType())) throw new InvalidProductImageException();
        ProductImageContentValidator.ValidatedImage valid;
        try (var content = storage.readBoundedIfMatch(sourceKey, metadata.getEtag(), ProductImageContentValidator.MAX_BYTES)) {
            valid = validator.validate(Files.readAllBytes(content.getPath()), upload.getContentType(), metadata.getLength(), alt);
        } catch (IOException failure) { throw new CatalogStorageUnavailableException(); }
        String sha = HexFormat.of().formatHex(valid.sha256());
        // Stable per-product/source/content identity makes concurrent attempts and retries converge.
        String durable = "images/products/" + product + "/" + source + "/" + sha;
        storage.copyIfMatch(sourceKey, metadata.getEtag(), durable, valid.contentType());
        var stored = storage.inspect(durable).orElseThrow(CatalogStorageUnavailableException::new);
        if (stored.getLength() != valid.byteSize() || !valid.contentType().equals(stored.getContentType()))
            throw new CatalogStorageUnavailableException();
        try (var copy = storage.readBoundedIfMatch(durable, stored.getEtag(), ProductImageContentValidator.MAX_BYTES)) {
            byte[] bytes = Files.readAllBytes(copy.getPath());
            var checked = validator.validate(bytes, valid.contentType(), valid.byteSize(), alt);
            if (!MessageDigest.isEqual(valid.sha256(), checked.sha256())) throw new CatalogStorageUnavailableException();
            checkpoint.save(durable, valid.contentType(), bytes);
        } catch (IOException failure) { throw new CatalogStorageUnavailableException(); }

        try {
            return transactions.execute(status -> {
                if (db.queryForList("SELECT id FROM products WHERE id=? FOR UPDATE", product).isEmpty())
                    throw new ProductNotFoundException();
                var retry = replay(product, source, alt, primary, order);
                if (retry != null) return retry;
                var gallery = db.queryForList("SELECT * FROM product_images WHERE product_id=? ORDER BY display_order", product);
                if (gallery.size() >= 10 || gallery.stream().anyMatch(i -> ((String) i.get("object_key")).endsWith("/" + sha)))
                    throw new CatalogConflictException();
                boolean isPrimary = primary == null ? gallery.isEmpty() : primary;
                if (gallery.isEmpty() && !isPrimary) throw new CatalogConflictException();
                int position = order == null ? (gallery.isEmpty() ? 0
                        : ((Number) gallery.getLast().get("display_order")).intValue() + 1) : order;
                if (position < 0 || gallery.stream().anyMatch(i -> ((Number) i.get("display_order")).intValue() == position))
                    throw new CatalogConflictException();
                if (isPrimary) db.update("UPDATE product_images SET is_primary=false WHERE product_id=? AND is_primary", product);
                UUID image = UUID.randomUUID();
                db.update("INSERT INTO product_images(id,product_id,source_image_id,object_key,alt_text,content_type,byte_size,width,height,display_order,is_primary,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,clock_timestamp())",
                        image, product, source, durable, alt, valid.contentType(), valid.byteSize(), valid.width(), valid.height(), position, isPrimary);
                return new Outcome(new Attached(image, product, alt, valid.width(), valid.height(), position, isPrimary), false);
            });
        } catch (DataIntegrityViolationException failure) { throw new CatalogConflictException(); }
    }

    private Outcome replay(UUID product, UUID source, String alt, Boolean primary, Integer order) {
        var rows = db.queryForList("SELECT * FROM product_images WHERE product_id=? AND source_image_id=?", product, source);
        if (rows.isEmpty()) return null;
        var row = rows.getFirst();
        if (!alt.equals(row.get("alt_text")) || primary != null && !primary.equals(row.get("is_primary"))
                || order != null && order != ((Number) row.get("display_order")).intValue()) throw new CatalogConflictException();
        return new Outcome(new Attached((UUID) row.get("id"), product, (String) row.get("alt_text"),
                ((Number) row.get("width")).intValue(), ((Number) row.get("height")).intValue(),
                ((Number) row.get("display_order")).intValue(), (Boolean) row.get("is_primary")), true);
    }
}
