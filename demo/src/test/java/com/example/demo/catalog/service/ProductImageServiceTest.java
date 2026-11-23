package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.example.demo.catalog.dto.request.AdminGalleryRequests.Attach;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Attachment;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Image;
import com.example.demo.catalog.exception.AdminCatalogException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductImageServiceTest {
    @Test void attach_shouldRejectUnversionedCalls_withoutFallback() {
        AdminGalleryService gallery=mock(AdminGalleryService.class);
        assertThatThrownBy(()->new ProductImageService(gallery).attach(UUID.randomUUID(),UUID.randomUUID(),8L,"Front",null,null)).isInstanceOf(AdminCatalogException.class);
        verifyNoInteractions(gallery);
    }
    @Test void attach_shouldDelegateSameCommand_andRetainLegacyMetadataAndCurrentRevisions() {
        AdminGalleryService gallery=mock(AdminGalleryService.class);UUID product=UUID.randomUUID(),source=UUID.randomUUID(),image=UUID.randomUUID();
        when(gallery.attach(eq(product),eq(8L),any())).thenReturn(new Attachment(new Image(image,product,"https://image.test/signed","Front",2,2,0,true),true,"8","3"));
        var result=new ProductImageService(gallery).attach(product,source,8L,"Front",null,null,8,3);
        assertThat(result.replay()).isTrue();assertThat(result.image().imageId()).isEqualTo(image);assertThat(result.productVersion()).isEqualTo("8");
        verify(gallery).attach(product,8L,new Attach(8,3,source,"Front",null,null));
    }
}
