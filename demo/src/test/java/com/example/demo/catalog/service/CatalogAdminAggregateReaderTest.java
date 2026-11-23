package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.converter.AdminCatalogConverter;
import com.example.demo.catalog.entity.*;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.*;
import com.example.demo.catalog.validator.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class CatalogAdminAggregateReaderTest {
    @Test void validate_shouldUseRealSharedRules_forPublishedPriceAndApplicableFacts() {
        var variants=mock(ProductVariantRepository.class);var images=mock(ProductImageRepository.class);
        var contexts=mock(CatalogSpecificationContextRepository.class);var revisions=mock(CatalogMutationRepository.class);
        var basic=new AdminCatalogValidator(new CatalogDataValidator(),new TechnicalSpecificationValidator(),new ProductImageContentValidator());
        var reader=new CatalogAdminAggregateReader(variants,images,contexts,revisions,basic,new ProductPublicationValidator(basic),new AdminCatalogConverter());
        var fixture=CatalogFixtures.publishedProduct(); var gallery=List.of(CatalogFixtures.image(fixture.product(),0,true));
        when(contexts.getApplicableFamilies(any())).thenReturn(Set.of());
        when(contexts.getApplicableFamilies(isNull())).thenReturn(Set.of());
        reader.validate(fixture.product(),List.of(fixture.variant()),gallery,true);
        fixture.variant().setSellingPrice(BigDecimal.ZERO);
        assertThatThrownBy(()->reader.validate(fixture.product(),List.of(fixture.variant()),gallery,true)).isInstanceOfSatisfying(AdminCatalogException.class,e->assertThat(e.getStatus()).isEqualTo(422));
        fixture.variant().setEnabled(false);
        reader.validate(fixture.product(),List.of(fixture.variant()),gallery,false);
        fixture.variant().setTechnicalSpecifications(Map.of("schema_version",1,"values",Map.of("physical_ram","wrong")));
        when(contexts.getApplicableFamilies(isNull())).thenReturn(Set.of("laptop"));
        assertThatThrownBy(()->reader.validate(fixture.product(),List.of(fixture.variant()),gallery,false)).isInstanceOf(AdminCatalogException.class);
    }
    @Test void detail_shouldSignOnlyCurrentGallery_andFailSafelyIfSignerWasNotPrepared() {
        var variants=mock(ProductVariantRepository.class);var images=mock(ProductImageRepository.class);
        var contexts=mock(CatalogSpecificationContextRepository.class);var revisions=mock(CatalogMutationRepository.class);
        var basic=new AdminCatalogValidator(new CatalogDataValidator(),new TechnicalSpecificationValidator(),new ProductImageContentValidator());
        var reader=new CatalogAdminAggregateReader(variants,images,contexts,revisions,basic,new ProductPublicationValidator(basic),new AdminCatalogConverter());
        var fixture=CatalogFixtures.publishedProduct();UUID id=UUID.randomUUID();org.springframework.test.util.ReflectionTestUtils.setField(fixture.product(),"id",id);
        var image=CatalogFixtures.image(fixture.product(),0,true);
        when(variants.findByProduct_IdOrderByIdAsc(id)).thenReturn(List.of(fixture.variant()));
        when(images.findByProduct_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(id)).thenReturn(List.of(image));
        when(contexts.getApplicableFamilies(isNull())).thenReturn(Set.of());
        assertThatThrownBy(()->reader.detail(fixture.product(),4,null)).isInstanceOfSatisfying(AdminCatalogException.class,e->assertThat(e.getStatus()).isEqualTo(503));
        var signer=mock(com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner.class);when(signer.signReadUrl(image.getObjectKey())).thenReturn("https://images.test/signed");
        var result=reader.detail(fixture.product(),4,signer);
        assertThat(result.getImages()).hasSize(1);assertThat(result.getImages().getFirst().getUrl()).isEqualTo("https://images.test/signed");
        assertThat(result.getPublicationReadiness().isEligible()).isTrue();assertThat(result.getTaxonomyVersion()).isEqualTo("4");
        verify(images,never()).findByProduct_IdOrderByDisplayOrderAsc(any());
    }

}
