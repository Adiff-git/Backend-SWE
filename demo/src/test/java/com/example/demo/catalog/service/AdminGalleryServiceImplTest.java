package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.never;
import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.converter.AdminCatalogConverter;
import com.example.demo.catalog.dto.request.AdminGalleryRequests.Attach;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.CatalogMutationRepository;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.validator.AdminCatalogValidator;
import com.example.demo.catalog.validator.CatalogDataValidator;
import com.example.demo.catalog.validator.ProductImageContentValidator;
import com.example.demo.catalog.validator.ProductPublicationValidator;
import com.example.demo.catalog.validator.TechnicalSpecificationValidator;
import com.example.demo.media.dto.response.PreparedProductImage;
import com.example.demo.media.service.MediaService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdminGalleryServiceImplTest {
    final ProductRepository products=mock(ProductRepository.class);
    final ProductImageRepository images=mock(ProductImageRepository.class);
    final CatalogMutationRepository revisions=mock(CatalogMutationRepository.class);
    final CatalogAggregateMutation mutations=mock(CatalogAggregateMutation.class);
    final CatalogAdminAggregateReader aggregates=mock(CatalogAdminAggregateReader.class);
    final CatalogImageAttachmentPreparer preparation=mock(CatalogImageAttachmentPreparer.class);
    final MediaService media=mock(MediaService.class);
    final ProductImageStorage storage=mock(ProductImageStorage.class);
    final UUID id=UUID.randomUUID(),source=UUID.randomUUID();
    final CatalogFixtures.ProductFixture fixture=CatalogFixtures.draftProduct();
    final List<ProductImage> rows=new ArrayList<>();
    final AdminGalleryServiceImpl service=new AdminGalleryServiceImpl(products,images,revisions,mutations,aggregates,preparation,media,storage,Clock.systemUTC(),new AdminCatalogConverter());
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(fixture.product(),"id",id);when(products.existsById(id)).thenReturn(true);
        when(images.findByProduct_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(id)).thenAnswer(call->rows.stream().filter(ProductImage::isActive).toList());
        when(images.findByProduct_IdAndSourceImageId(id,source)).thenAnswer(call->rows.stream().filter(i->source.equals(i.getSourceImageId())).findFirst());
        when(images.save(any())).thenAnswer(call->{ProductImage image=call.getArgument(0);if(!rows.contains(image))rows.add(image);return image;});
        when(aggregates.variants(fixture.product())).thenReturn(List.of(fixture.variant()));
        var basic=new AdminCatalogValidator(new CatalogDataValidator(),new TechnicalSpecificationValidator(),new ProductImageContentValidator());
        var publication=new ProductPublicationValidator(basic);
        doAnswer(call->{List<ProductImage> active=call.getArgument(2);if((Boolean)call.getArgument(3))publication.requireValid(fixture.product(),List.of(fixture.variant()),active,Set.of());else basic.validateBasic(fixture.product(),List.of(fixture.variant()),active,Set.of());return null;})
                .when(aggregates).validate(eq(fixture.product()),any(),any(),any(Boolean.class));
        var signer=mock(ProductImageStorage.PreparedReadSigner.class);when(signer.signReadUrl(any())).thenReturn("https://images.test/signed");when(storage.prepareReadSigner()).thenReturn(signer);
        doAnswer(call->{CatalogAggregateMutation.ProductCommand<?> command=call.getArgument(3);return command.noOp(fixture.product())?command.unchanged(fixture.product()):command.apply(fixture.product());})
                .when(mutations).product(eq(id),anyLong(),anyLong(),any());
    }
    ProductImage image(int position,boolean primary) {
        ProductImage image=CatalogFixtures.image(fixture.product(),position,primary);ReflectionTestUtils.setField(image,"id",UUID.randomUUID());rows.add(image);return image;
    }
    @Test void reorder_shouldUseDisjointTemporaryGaps_withIntegerMaxPosition_andPreservePrimary() {
        ProductImage first=image(Integer.MAX_VALUE,true),second=image(4,false);
        List<List<Integer>> stages=new ArrayList<>();doAnswer(call->{stages.add(rows.stream().map(ProductImage::getDisplayOrder).toList());return null;}).when(revisions).flush();
        service.reorder(id,0,0,List.of(second.getId(),first.getId()));
        assertThat(second.getDisplayOrder()).isZero();assertThat(first.getDisplayOrder()).isEqualTo(1);assertThat(first.isPrimary()).isTrue();
        assertThat(stages.getFirst()).doesNotContain(0,1,4,Integer.MAX_VALUE);verify(revisions).lockImages(eq(id),any());
    }
    @Test void reorder_shouldRejectDuplicateOrIncompletePermutation_withoutWrites() {
        ProductImage first=image(0,true);image(1,false);
        assertThatThrownBy(()->service.reorder(id,0,0,List.of(first.getId(),first.getId()))).isInstanceOf(AdminCatalogException.class);
        assertThat(rows).extracting(ProductImage::getDisplayOrder).containsExactly(0,1);
    }
    @Test void remove_shouldRejectPublishedPrimaryWithoutReplacement_andRetainReferenceWithExplicitReplacement() {
        fixture.product().setStatus(ProductStatus.PUBLISHED);ProductImage first=image(0,true),second=image(1,false);
        assertThatThrownBy(()->service.remove(id,first.getId(),0,0,null)).isInstanceOf(AdminCatalogException.class);
        assertThat(first.isActive()).isTrue();service.remove(id,first.getId(),0,0,second.getId());
        assertThat(first.isActive()).isFalse();assertThat(first.isPrimary()).isFalse();assertThat(second.isPrimary()).isTrue();
        verify(images,never()).delete(any());
    }
    @Test void remove_shouldAllowLastDraftImage_butNeverRemoveLastPublishedImage() {
        ProductImage only=image(0,true);fixture.product().setStatus(ProductStatus.PUBLISHED);
        assertThatThrownBy(()->service.remove(id,only.getId(),0,0,null)).isInstanceOf(AdminCatalogException.class);
        fixture.product().setStatus(ProductStatus.DRAFT);service.remove(id,only.getId(),0,0,null);assertThat(only.isActive()).isFalse();
    }
    @Test void attach_shouldRejectStaleBeforeRevalidationAndReplay() {
        ProductImage only=image(0,true);only.setSourceImageId(source);
        PreparedProductImage proof=new PreparedProductImage(source,8L,0L,"private-source","image/png");
        when(media.prepareProductImage(8L,source)).thenReturn(proof);
        when(preparation.prepare(id,proof,"Product image",only)).thenReturn(new CatalogImageAttachmentPreparer.Prepared(proof,only.getObjectKey(),"image/png",100,10,10,false));
        reset(mutations);when(mutations.product(eq(id),eq(0L),eq(0L),any())).thenThrow(new AdminCatalogException(409,"CATALOG_STALE_VERSION","Reload"));
        assertThatThrownBy(()->service.attach(id,8L,new Attach(0,0,source,"Product image",null,null))).isInstanceOf(AdminCatalogException.class);
        verify(media,never()).validatePreparedProductImage(anyLong(),any());
    }
    @Test void attach_shouldReactivateRetainedAssociationIdentity_afterContentAndOwnerProof() {
        ProductImage old=image(0,false);old.setActive(false);old.setSourceImageId(source);UUID retained=old.getId();
        PreparedProductImage proof=new PreparedProductImage(source,8L,0L,"source","image/png");when(media.prepareProductImage(8L,source)).thenReturn(proof);
        when(preparation.prepare(id,proof,"Front",old)).thenReturn(new CatalogImageAttachmentPreparer.Prepared(proof,old.getObjectKey(),"image/png",100,10,10,true));
        service.attach(id,8L,new Attach(0,0,source,"Front",null,null));
        assertThat(old.getId()).isEqualTo(retained);assertThat(old.isActive()).isTrue();assertThat(old.isPrimary()).isTrue();
        verify(media).validatePreparedProductImage(8L,proof);verify(images,never()).delete(any());
    }
    @Test void attach_shouldRejectForeignOrPendingMedia_beforeStoragePreparation() {
        when(media.prepareProductImage(8L,source)).thenThrow(new com.example.demo.media.exception.ImageUploadNotFoundException());
        assertThatThrownBy(()->service.attach(id,8L,new Attach(0,0,source,"Front",null,null))).isInstanceOf(com.example.demo.media.exception.ImageUploadNotFoundException.class);
        verifyNoInteractions(preparation,storage,mutations);
    }
    void prepared(ProductImage existing,String alt,boolean verified,String key){
        PreparedProductImage proof=new PreparedProductImage(source,8L,0L,"source","image/png");when(media.prepareProductImage(8L,source)).thenReturn(proof);
        when(preparation.prepare(id,proof,alt,existing)).thenReturn(new CatalogImageAttachmentPreparer.Prepared(proof,key,"image/png",100,10,10,verified));
    }
    @Test void replay_shouldReturnSameAssociationWithoutWriting_butRejectDifferentMetadata() {
        ProductImage existing=image(0,true);existing.setSourceImageId(source);prepared(existing,"Product image",false,existing.getObjectKey());
        var result=service.attach(id,8L,new Attach(0,0,source,"Product image",null,null));assertThat(result.isReplay()).isTrue();assertThat(result.getImage().getId()).isEqualTo(existing.getId());verify(images,never()).save(any());
        prepared(existing,"Changed",false,existing.getObjectKey());assertThatThrownBy(()->service.attach(id,8L,new Attach(0,0,source,"Changed",null,null))).isInstanceOf(AdminCatalogException.class);
        assertThat(existing.getAltText()).isEqualTo("Product image");
    }
    @Test void attach_shouldRejectGalleryLimitAndDuplicateContent_withoutSavingOrChangingPrimary() {
        for(int position=0;position<10;position++)image(position,position==0);prepared(null,"Front",true,"images/products/new/hash");
        assertThatThrownBy(()->service.attach(id,8L,new Attach(0,0,source,"Front",null,null))).isInstanceOf(AdminCatalogException.class);assertThat(rows).hasSize(10);
        rows.clear();ProductImage old=image(0,true);old.setObjectKey("images/existing/hash");
        assertThatThrownBy(()->service.attach(id,8L,new Attach(0,0,source,"Front",null,null))).isInstanceOf(AdminCatalogException.class);
        assertThat(old.isPrimary()).isTrue();verify(images,never()).save(any());
    }
    @Test void attach_shouldRejectOccupiedPositionOrExplicitFirstNonPrimary_beforeGalleryWrites() {
        ProductImage old=image(0,true);prepared(null,"Front",true,"images/products/new/new-hash");
        assertThatThrownBy(()->service.attach(id,8L,new Attach(0,0,source,"Front",false,0))).isInstanceOf(AdminCatalogException.class);assertThat(old.isPrimary()).isTrue();
        rows.clear();assertThatThrownBy(()->service.attach(id,8L,new Attach(0,0,source,"Front",false,null))).isInstanceOf(AdminCatalogException.class);verify(images,never()).save(any());
    }
    @Test void checkpointFailure_shouldPreventEnteringMutationBoundary() {
        PreparedProductImage proof=new PreparedProductImage(source,8L,0L,"source","image/png");when(media.prepareProductImage(8L,source)).thenReturn(proof);
        when(preparation.prepare(id,proof,"Front",null)).thenThrow(new com.example.demo.catalog.exception.CatalogStorageUnavailableException());
        assertThatThrownBy(()->service.attach(id,8L,new Attach(0,0,source,"Front",null,null))).isInstanceOf(com.example.demo.catalog.exception.CatalogStorageUnavailableException.class);
        verifyNoInteractions(mutations);verify(images,never()).save(any());
    }

}
