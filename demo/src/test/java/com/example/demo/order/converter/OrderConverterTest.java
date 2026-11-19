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
    }
}
