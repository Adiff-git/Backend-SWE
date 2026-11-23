package com.example.demo.catalog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.catalog.validator.ProductImageContentValidator;
import com.example.demo.storage.dto.response.StoredObjectContent;
import com.example.demo.storage.dto.response.StoredObjectMetadata;
import com.example.demo.storage.exception.StorageUnavailableException;
import com.example.demo.storage.exception.StoredObjectChangedException;
import com.example.demo.storage.service.ObjectStorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditImageReaderTest {
    @TempDir Path temporary;
    private final ObjectStorageService storage = mock(ObjectStorageService.class);

    @Test
    void inspect_shouldReuseBoundedReaderAndRealDecoder_withoutAnyObjectWrite() throws Exception {
        byte[] bytes = png();
        StoredObjectMetadata metadata = new StoredObjectMetadata("etag", bytes.length, "image/png");
        Path content = setup(bytes, metadata);
        try (AuditImageReader reader = new AuditImageReader(storage)) {
            assertThat(reader.inspect(image(bytes.length, 2))).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        }
        assertThat(content).doesNotExist();
        verify(storage).inspect("private/key");
        verify(storage).readBoundedIfMatch("private/key", "etag", ProductImageContentValidator.MAX_BYTES);
        verifyNoMoreInteractions(storage);
    }

    @Test
    void inspect_shouldReportKnownMissingObject_withoutCallingGetOrDelete() throws Exception {
        when(storage.inspect("private/key")).thenReturn(Optional.empty());
        try (AuditImageReader reader = new AuditImageReader(storage)) {
            assertThat(reader.inspect(image(10, 2))).isNull();
        }
        verify(storage).inspect("private/key");
        verifyNoMoreInteractions(storage);
    }

    @Test
    void inspect_shouldRejectMismatchedDimensionsAndInvalidBytes_withoutCopyOrCheckpoint() throws Exception {
        byte[] bytes = png();
        setup(bytes, new StoredObjectMetadata("etag", bytes.length, "image/png"));
        try (AuditImageReader reader = new AuditImageReader(storage)) {
            assertThat(reader.inspect(image(bytes.length, 3))).isNull();
        }
        setup(new byte[] {1, 2}, new StoredObjectMetadata("etag", 2, "image/png"));
        try (AuditImageReader reader = new AuditImageReader(storage)) {
            assertThat(reader.inspect(image(2, 2))).isNull();
        }
    }

    @Test
    void inspect_shouldPropagateUnavailableOrChangedObjects_asIncompleteRatherThanCleanData() throws Exception {
        when(storage.inspect("private/key")).thenThrow(new StorageUnavailableException());
        try (AuditImageReader reader = new AuditImageReader(storage)) {
            assertThatThrownBy(() -> reader.inspect(image(2, 2))).isInstanceOf(StorageUnavailableException.class);
        }
        byte[] bytes = png();
        setup(bytes, new StoredObjectMetadata("changed-etag", bytes.length, "image/png"));
        try (AuditImageReader reader = new AuditImageReader(storage)) {
            assertThatThrownBy(() -> reader.inspect(image(bytes.length, 2))).isInstanceOf(StoredObjectChangedException.class);
        }
    }

    @Test
    void inspect_shouldBoundInputBeforeAnyStorageOperation_andInitializeNothingForNoImages() throws Exception {
        try (AuditImageReader reader = new AuditImageReader(storage)) {
            assertThat(reader.inspect(image(ProductImageContentValidator.MAX_BYTES + 1L, 2))).isNull();
        }
        verifyNoMoreInteractions(storage);
        try (AuditImageReader ignored = new AuditImageReader(Map.of())) {
            // Creating/closing the CLI adapter alone needs no credentials, client or Spring context.
        }
    }

    private Path setup(byte[] bytes, StoredObjectMetadata returned) throws Exception {
        StoredObjectMetadata head = new StoredObjectMetadata("etag", bytes.length, "image/png");
        Path path = Files.createTempFile(temporary, "bounded-", ".png");
        Files.write(path, bytes);
        org.mockito.Mockito.doReturn(Optional.of(head)).when(storage).inspect("private/key");
        when(storage.readBoundedIfMatch("private/key", "etag", ProductImageContentValidator.MAX_BYTES))
                .thenReturn(new StoredObjectContent(path, returned));
        return path;
    }

    private CatalogCompatibilityAudit.Image image(long bytes, int width) {
        return new CatalogCompatibilityAudit.Image("image-id", "product-id", "private/key", "Image", "image/png", bytes, width, 2);
    }

    private byte[] png() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }
}
