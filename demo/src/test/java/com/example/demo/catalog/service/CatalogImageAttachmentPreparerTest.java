package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.exception.InvalidProductImageException;
import com.example.demo.catalog.storage.ProductImageCheckpoint;
import com.example.demo.catalog.validator.ProductImageContentValidator;
import com.example.demo.media.dto.response.PreparedProductImage;
import com.example.demo.media.testdata.MediaTestData;
import com.example.demo.storage.dto.response.StoredObjectContent;
import com.example.demo.storage.dto.response.StoredObjectMetadata;
import com.example.demo.storage.service.ObjectStorageService;
import java.nio.file.Files;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogImageAttachmentPreparerTest {
    final ObjectStorageService storage=mock(ObjectStorageService.class);
    final ProductImageCheckpoint checkpoint=mock(ProductImageCheckpoint.class);
    final CatalogImageAttachmentPreparer preparer=new CatalogImageAttachmentPreparer(storage,checkpoint,new ProductImageContentValidator());
    final UUID product=UUID.randomUUID();
    final PreparedProductImage proof=new PreparedProductImage(UUID.randomUUID(),8L,0L,"source/key","image/png");
    void content(byte[] bytes) {
        when(storage.inspect(anyString())).thenReturn(Optional.of(new StoredObjectMetadata("etag",bytes.length,"image/png")));
        when(storage.readBoundedIfMatch(anyString(),eq("etag"),anyLong())).thenAnswer(call->{var file=Files.createTempFile("admin-gallery-test-",".png");Files.write(file,bytes);return new StoredObjectContent(file,new StoredObjectMetadata("etag",bytes.length,"image/png"));});
    }
    @Test void prepare_shouldBoundAndValidateContentCopyHashAndCheckpoint_withoutDeletingSharedSource() throws Exception {
        byte[] bytes=MediaTestData.fixture("valid.png");content(bytes);
        var prepared=preparer.prepare(product,proof,"Front",null);
        assertThat(prepared.contentVerified()).isTrue();assertThat(prepared.key()).startsWith("images/products/"+product+"/"+proof.getId()+"/");
        verify(storage).copyIfMatch(eq(proof.getFinalKey()),eq("etag"),eq(prepared.key()),eq("image/png"));
        verify(checkpoint).save(prepared.key(),"image/png",bytes);verify(storage,never()).deleteStaging(any());
    }
    @Test void prepare_shouldRejectOversizeBeforeReadingOrCopying() {
        when(storage.inspect(proof.getFinalKey())).thenReturn(Optional.of(new StoredObjectMetadata("etag",10_485_761,"image/png")));
        assertThatThrownBy(()->preparer.prepare(product,proof,"Front",null)).isInstanceOf(InvalidProductImageException.class);
        verify(storage,never()).readBoundedIfMatch(any(),any(),anyLong());verify(storage,never()).copyIfMatch(any(),any(),any(),any());
    }
    @Test void prepare_shouldRejectInvalidBytesBeforeCopyAndPublication() {
        content(new byte[]{1,2,3});assertThatThrownBy(()->preparer.prepare(product,proof,"Front",null)).isInstanceOf(InvalidProductImageException.class);
        verify(storage,never()).copyIfMatch(any(),any(),any(),any());verifyNoInteractions(checkpoint);
    }
    @Test void prepare_shouldRetainCopiedObjectWhenCheckpointFails_andNeverGuessDelete() throws Exception {
        content(MediaTestData.fixture("valid.png"));doThrow(new CatalogStorageUnavailableException()).when(checkpoint).save(any(),any(),any());
        assertThatThrownBy(()->preparer.prepare(product,proof,"Front",null)).isInstanceOf(CatalogStorageUnavailableException.class);
        verify(storage,never()).deleteStaging(any());verify(checkpoint,never()).delete(any());
    }
    @Test void prepare_shouldAvoidObjectWritesForExistingActiveReplayCandidate() {
        var fixture=CatalogFixtures.draftProduct();var image=CatalogFixtures.image(fixture.product(),0,true);
        assertThat(preparer.prepare(product,proof,"Product image",image).contentVerified()).isFalse();verifyNoInteractions(storage,checkpoint);
    }
    @Test void prepare_shouldRejectCorruptDurableCopy_beforeCheckpoint_andRetainObjectsForSafeReconciliation() throws Exception {
        byte[] bytes=MediaTestData.fixture("valid.png");content(bytes);
        when(storage.readBoundedIfMatch(org.mockito.ArgumentMatchers.startsWith("images/products/"),eq("etag"),anyLong())).thenAnswer(call->{
            var file=Files.createTempFile("corrupt-gallery-copy-",".png");Files.write(file,new byte[]{1,2,3});return new StoredObjectContent(file,new StoredObjectMetadata("etag",bytes.length,"image/png"));
        });
        assertThatThrownBy(()->preparer.prepare(product,proof,"Front",null)).isInstanceOf(InvalidProductImageException.class);
        verifyNoInteractions(checkpoint);verify(storage,never()).deleteStaging(any());
    }

}
