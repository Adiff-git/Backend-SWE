package com.example.demo.catalog.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.catalog.storage.ProductImageCheckpoint;
import com.example.demo.storage.config.S3StorageProperties;
import com.example.demo.storage.service.ObjectStorageService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

class LegacyProductImageUploadCleanupTest {
    @Test void cleanup_shouldRetainDurableObjectWhenAnyAssociationIncludingInactiveReferencesIt(){
        var jdbc=mock(JdbcTemplate.class);var storage=mock(ObjectStorageService.class);var s3=mock(S3Client.class);
        var checkpoint=mock(ProductImageCheckpoint.class);var properties=new S3StorageProperties();
        UUID product=UUID.randomUUID(),upload=UUID.randomUUID();String key="images/products/"+product+"/"+upload+"/object";
        when(jdbc.queryForList("SELECT * FROM product_image_uploads WHERE state IN ('PUBLISHED','EXPIRED','FAILED')"))
                .thenReturn(List.of(Map.of("state","EXPIRED","staging_key","staging","durable_key",key,"product_id",product,"id",upload)));
        // Retained inactive rows are references too; the count intentionally has no active predicate.
        when(jdbc.queryForObject("SELECT count(*) FROM product_images WHERE object_key=?",Integer.class,key)).thenReturn(1);
        new LegacyProductImageUploadCleanup(jdbc,storage,properties,s3,checkpoint).cleanup();
        verify(storage).deleteStaging("staging");verify(s3,never()).deleteObject(any(java.util.function.Consumer.class));
        verify(s3,never()).deleteObject(any(DeleteObjectRequest.class));verify(checkpoint,never()).delete(key);
    }
}
