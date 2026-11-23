package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.converter.AdminCatalogConverter;
import com.example.demo.catalog.dto.request.AdminVariantRequests.*;
import com.example.demo.catalog.dto.request.CatalogField;
import com.example.demo.catalog.entity.*;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminVariantServiceImplTest {
    private final CatalogAggregateMutation mutations=mock(CatalogAggregateMutation.class);
    private final CatalogMutationRepository revisions=mock(CatalogMutationRepository.class);
    private final ProductVariantRepository variants=mock(ProductVariantRepository.class);
    private final CatalogAdminAggregateReader aggregates=mock(CatalogAdminAggregateReader.class);
    private final AdminVariantServiceImpl service=new AdminVariantServiceImpl(mutations,revisions,variants,aggregates,new AdminCatalogConverter());
    private final UUID productId=UUID.randomUUID(),variantId=UUID.randomUUID();
    private final CatalogFixtures.ProductFixture fixture=CatalogFixtures.draftProduct();
    private List<ProductVariant> siblings;
    @BeforeEach void setup() {
        org.springframework.test.util.ReflectionTestUtils.setField(fixture.product(),"id",productId);
        org.springframework.test.util.ReflectionTestUtils.setField(fixture.variant(),"id",variantId);
        siblings=new ArrayList<>(List.of(fixture.variant()));
        when(aggregates.variants(fixture.product())).thenAnswer(call->List.copyOf(siblings));
        when(variants.save(any())).thenAnswer(call->{ProductVariant v=call.getArgument(0);siblings.add(v);return v;});
        doAnswer(call->{CatalogAggregateMutation.ProductCommand<?> command=call.getArgument(3);
            if(command.noOp(fixture.product())) return command.unchanged(fixture.product());
            return command.apply(fixture.product());}).when(mutations).product(eq(productId),anyLong(),anyLong(),any());
    }
    @Test void create_shouldKeepSkuAndPriceRequired_withoutGuessingSecondVariantOptions() {
        Create invalid=new Create(0,0," ",BigDecimal.ONE,Map.of(),Map.of("schema_version",1,"values",Map.of()),true);
        assertThatThrownBy(()->service.create(productId,invalid)).isInstanceOf(AdminCatalogException.class);
        verify(variants,never()).save(any());
        service.create(productId,new Create(0,0,"new",BigDecimal.ZERO,Map.of("choice","new"),Map.of("schema_version",1,"values",Map.of()),false));
        assertThat(siblings).hasSize(2);assertThat(siblings.getLast().isEnabled()).isFalse();
        assertThat(siblings.getLast().getSellingPrice()).isEqualByComparingTo("0");
        verify(aggregates).validate(fixture.product(),siblings,List.of(),false);
    }
    @Test void patch_shouldUseProductClaimBeforeOwnedChildLock_andPreserveOmittedDocuments() {
        Map<String,Object> facts=fixture.variant().getTechnicalSpecifications();
        var request=new Patch(0,0,CatalogField.absent(),CatalogField.supplied(new BigDecimal("999999999999999999999999999999")),CatalogField.absent(),CatalogField.absent());
        service.update(productId,variantId,request);
        assertThat(fixture.variant().getSellingPrice()).isEqualByComparingTo("999999999999999999999999999999");
        assertThat(fixture.variant().getTechnicalSpecifications()).isSameAs(facts);
        verify(revisions).lockVariants(productId,List.of(variantId));
    }
    @Test void identity_shouldRejectCaseInsensitiveSkuAndRetainedOptionConflicts() {
        service.create(productId,new Create(0,0,"SECOND",BigDecimal.TEN,Map.of("choice","second"),Map.of("schema_version",1,"values",Map.of()),false));
        when(variants.hasSkuConflict("second",variantId)).thenReturn(true); // SQL LOWER behavior remains real-DB proof in T033.
        assertThatThrownBy(()->service.update(productId,variantId,new Patch(0,0,CatalogField.supplied("second"),CatalogField.absent(),CatalogField.absent(),CatalogField.absent())))
                .isInstanceOfSatisfying(AdminCatalogException.class,e->assertThat(e.getCode()).isEqualTo("CATALOG_SKU_CONFLICT"));
    }
    @Test void enable_shouldAllowArchivedException_butDisableAndInformationEditsRequireRestore() {
        fixture.product().setStatus(ProductStatus.ARCHIVED);fixture.variant().setEnabled(false);
        service.setEnabled(productId,variantId,0,0,true);
        assertThat(fixture.variant().isEnabled()).isTrue();assertThat(fixture.product().getStatus()).isEqualTo(ProductStatus.ARCHIVED);
        assertThatThrownBy(()->service.setEnabled(productId,variantId,0,0,false)).isInstanceOf(AdminCatalogException.class);
        assertThatThrownBy(()->service.update(productId,variantId,new Patch(0,0,CatalogField.supplied("edit"),CatalogField.absent(),CatalogField.absent(),CatalogField.absent())))
                .isInstanceOf(AdminCatalogException.class);
    }
    @Test void disable_shouldValidateWholeResultingPublishedAggregate_andPropagateFailure() {
        fixture.product().setStatus(ProductStatus.PUBLISHED);
        doThrow(new AdminCatalogException(422,"CATALOG_PUBLICATION_INVALID","Last Enabled variant")).when(aggregates).validate(eq(fixture.product()),anyList(),anyList(),eq(true));
        assertThatThrownBy(()->service.setEnabled(productId,variantId,0,0,false)).isInstanceOf(AdminCatalogException.class);
        // Only a real transaction test can prove the mutated entity/revision is rolled back.
        verify(revisions).lockVariants(productId,List.of(variantId));
    }
    @Test void nestedOwnership_shouldRejectUnknownChild_withoutSaving() {
        assertThatThrownBy(()->service.update(productId,UUID.randomUUID(),new Patch(0,0,CatalogField.supplied("edit"),CatalogField.absent(),CatalogField.absent(),CatalogField.absent())))
                .isInstanceOfSatisfying(AdminCatalogException.class,e->assertThat(e.getStatus()).isEqualTo(404));
        verify(variants,never()).save(any());
    }
    @Test void specifications_shouldReplaceOnlyAddressedDocument_andLeaveSiblingHistoryAndOtherFields() {
        ProductVariant other=CatalogFixtures.variant(fixture.product(),Map.of("choice","other"),BigDecimal.TEN);
        org.springframework.test.util.ReflectionTestUtils.setField(other,"id",UUID.randomUUID());siblings.add(other);
        var priorOther=other.getTechnicalSpecifications();var priorOptions=fixture.variant().getOptionCombination();var priorPrice=fixture.variant().getSellingPrice();
        var document=Map.<String,Object>of("schema_version",1,"values",Map.of("feature",false,"counter",0));
        service.replaceSpecifications(productId,variantId,new Patch(0,0,CatalogField.absent(),CatalogField.absent(),CatalogField.absent(),CatalogField.supplied(document)));
        assertThat(fixture.variant().getTechnicalSpecifications()).isEqualTo(document);
        assertThat(fixture.variant().getOptionCombination()).isSameAs(priorOptions);
        assertThat(fixture.variant().getSellingPrice()).isSameAs(priorPrice);assertThat(other.getTechnicalSpecifications()).isSameAs(priorOther);
        service.replaceSpecifications(productId,variantId,new Patch(0,0,CatalogField.absent(),CatalogField.absent(),CatalogField.absent(),CatalogField.supplied(Map.of("schema_version",1,"values",Map.of()))));
        assertThat(fixture.variant().getTechnicalSpecifications()).containsEntry("values",Map.of());
    }

    @Test void specifications_shouldTreatEqualJsonIntegerRepresentationsAsCurrentNoOp() {
        service.replaceSpecifications(productId,variantId,new Patch(0,0,CatalogField.absent(),CatalogField.absent(),CatalogField.absent(),
                CatalogField.supplied(Map.of("schema_version",java.math.BigInteger.ONE,"values",Map.of()))));
        verify(revisions,never()).lockVariants(any(),any());
        verify(aggregates).validateCurrent(fixture.product());
    }

}
