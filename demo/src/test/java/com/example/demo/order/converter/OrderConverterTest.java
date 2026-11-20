package com.example.demo.order.converter;

import com.example.demo.order.dto.response.OrderReadSeed;
import com.example.demo.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderConverterTest {
    private final OrderConverter converter = new OrderConverter();

    @Test void toInventorySeed_shouldMapOnlyPreparedIdentitiesAndPreserveManagedParticipation() {
        var order = com.example.demo.testdata.order.OrderTestData.order(1L, OrderStatus.PENDING).id(7L).inventoryManaged(true).build();
        var row = org.mockito.Mockito.mock(com.example.demo.order.repository.OrderItemRepository.ItemView.class);
        java.util.UUID variant = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(row.getId()).thenReturn(9L);
        org.mockito.Mockito.when(row.getProductVariantId()).thenReturn(variant);
        org.mockito.Mockito.when(row.getQuantity()).thenReturn(3);
        var seed = converter.toInventorySeed(order, List.of(row));
        assertThat(seed.isManaged()).isTrue();
        assertThat(seed.getId()).isEqualTo(7L);
        assertThat(seed.getOwnerId()).isEqualTo(1L);
        assertThat(seed.getItems().getFirst().getVariantId()).isEqualTo(variant);
        assertThat(seed.getItems().getFirst().getQuantity()).isEqualTo(3);
        assertThatThrownBy(() -> seed.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void toPage_shouldUseHistoricalPreviewAndDistinctRowCount() {
        OrderReadSeed.Header header = new OrderReadSeed.Header(7L,"ORDER7",Instant.EPOCH,OrderStatus.PENDING,
                BigDecimal.valueOf(90),BigDecimal.valueOf(100),"Recipient","Phone","Address",null,null,null);
        OrderReadSeed.Item item = new OrderReadSeed.Item(9L,7L,null,null,"Old name","Old SKU",Map.of(),8,
                BigDecimal.TEN,BigDecimal.valueOf(80),5);
        com.example.demo.order.dto.response.OrderPage page = converter.toPage(new OrderReadSeed.Page(List.of(header),List.of(item),0,20,1),Map.of());
        assertThat(page.getItems().getFirst().getItemCount()).isEqualTo(5);
        assertThat(page.getItems().getFirst().getRemainingItemCount()).isEqualTo(2);
        assertThat(page.getItems().getFirst().getPayableTotal().getAmount()).isEqualTo(BigInteger.valueOf(90));
        assertThat(page.getItems().getFirst().getItemPreview().getFirst().getProductNameSnapshot()).isEqualTo("Old name");
        assertThat(page.getItems().getFirst().getItemPreview().getFirst().getPrimaryImageUrl()).isNull();
    }

    @Test void toPage_shouldPreserveLargeCountMetadataAndRejectUnrepresentablePageCount() {
        assertThat(converter.toPage(new OrderReadSeed.Page(List.of(),List.of(),1073741824,2,2147483649L),Map.of()).getTotalPages())
                .isEqualTo(1073741825);
        assertThatThrownBy(() -> converter.toPage(new OrderReadSeed.Page(List.of(),List.of(),0,1,2147483649L),Map.of()))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test void toDetail_shouldExposeCompleteSnapshotsWithoutLiveReferences() {
        OrderReadSeed.Header header = new OrderReadSeed.Header(7L,"ORDER7",Instant.EPOCH,OrderStatus.COMPLETED,
                BigDecimal.valueOf(90),BigDecimal.valueOf(100),"Old recipient","Old phone","Old address",null,Instant.EPOCH.plusSeconds(1),null);
        OrderReadSeed.Item item = new OrderReadSeed.Item(9L,7L,null,null,"Old product","Old SKU",Map.of("color","Blue"),2,
                BigDecimal.valueOf(50),BigDecimal.valueOf(100),1);
        com.example.demo.order.dto.response.OrderDetailResponse detail = converter.toDetail(new OrderReadSeed.Detail(header,List.of(item)),Map.of());
        assertThat(detail.getDeliveryAddressSnapshot()).isEqualTo("Old address");
        assertThat(detail.getItems().getFirst().getProductVariantId()).isNull();
        assertThat(detail.getItems().getFirst().getUnitPriceSnapshot().getAmount()).isEqualTo(BigInteger.valueOf(50));
        assertThat(detail.getMerchandiseTotal().getAmount()).isEqualTo(BigInteger.valueOf(100));
        assertThat(detail.getVoucherDiscount().getAmount()).isZero();
        assertThat(detail.getVoucher()).isNull();
    }
    @Test void history_shouldUseOnlyPreparedOriginalVoucherSnapshot() {
        var money=new com.example.demo.catalog.dto.response.SellingPrice(BigInteger.valueOf(100),"VND");
        var discount=new com.example.demo.catalog.dto.response.SellingPrice(BigInteger.TEN,"VND");
        var snapshot=new com.example.demo.voucher.dto.response.VoucherSnapshot(11L,12L,"OLD","Original title","FIXED_AMOUNT",BigDecimal.TEN,
            new com.example.demo.catalog.dto.response.SellingPrice(BigInteger.ZERO,"VND"),null,money,discount,Instant.EPOCH);
        var header=new OrderReadSeed.Header(7L,"ORDER7",Instant.EPOCH,OrderStatus.PENDING,BigDecimal.valueOf(90),BigDecimal.valueOf(100),"Name","Phone","Address",null,null,null,BigDecimal.TEN,snapshot);
        var detail=converter.toDetail(new OrderReadSeed.Detail(header,List.of()),Map.of());
        var summary=converter.toPage(new OrderReadSeed.Page(List.of(header),List.of(),0,20,1),Map.of()).getItems().getFirst();
        assertThat(detail.getVoucher()).isSameAs(snapshot);
        assertThat(detail.getVoucherDiscount().getAmount()).isEqualTo(BigInteger.TEN);
        assertThat(summary.getVoucher().getCode()).isEqualTo("OLD");
        assertThat(summary.getVoucher().getTitle()).isEqualTo("Original title");
        assertThat(summary.getVoucherDiscount().getAmount()).isEqualTo(BigInteger.TEN);
        assertThat(summary.getPayableTotal().getAmount()).isEqualTo(BigInteger.valueOf(90));
    }
}
