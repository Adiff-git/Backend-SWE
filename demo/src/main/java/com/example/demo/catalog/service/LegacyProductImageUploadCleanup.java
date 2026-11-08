package com.example.demo.catalog.service;

import com.example.demo.catalog.storage.ProductImageCheckpoint;
import com.example.demo.storage.service.ObjectStorageService;
import com.example.demo.storage.config.S3StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;

/** Reconciles sessions created before uploads were unified; creates no new sessions. */
@Service @RequiredArgsConstructor
public class LegacyProductImageUploadCleanup {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LegacyProductImageUploadCleanup.class);
    private final JdbcTemplate db;
    private final ObjectStorageService storage;
    private final S3StorageProperties properties;
    private final S3Client s3;
    private final ProductImageCheckpoint checkpoint;
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${catalog.upload-cleanup-delay-ms:60000}")
    public void cleanup() {
        db.update("UPDATE product_image_uploads SET state='EXPIRED',claim_token=NULL,claim_expires_at=NULL WHERE state IN ('PREPARED','VERIFIED') AND expires_at<=clock_timestamp() AND (claim_expires_at IS NULL OR claim_expires_at<=clock_timestamp())");
        for(var row:db.queryForList("SELECT * FROM product_image_uploads WHERE state IN ('PUBLISHED','EXPIRED','FAILED')")) {
            try {
                storage.deleteStaging((String)row.get("staging_key"));
                String key=(String)row.get("durable_key");
                if(!"PUBLISHED".equals(row.get("state")) && key!=null && key.startsWith("images/products/"+row.get("product_id")+"/"+row.get("id")+"/") && db.queryForObject("SELECT count(*) FROM product_images WHERE object_key=?",Integer.class,key)==0)
                    { s3.deleteObject(r->r.bucket(properties.getBucket()).key(key)); checkpoint.delete(key); }
            } catch(RuntimeException failure) { LOG.warn("Product upload cleanup pending for session {}",row.get("id")); }
        }
    }
}
