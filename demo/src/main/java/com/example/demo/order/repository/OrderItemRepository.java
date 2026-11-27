package com.example.demo.order.repository;

import com.example.demo.order.entity.OrderItem;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem,Long> {
    @Query(value="SELECT id,CAST(variant_options_snapshot AS TEXT) FROM order_items WHERE id IN (:ids) ORDER BY id",nativeQuery=true)
    List<Object[]> findReviewOptionSnapshots(@Param("ids") Collection<Long> ids);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM OrderItem i WHERE i.id=:item AND i.order.id=:order")
    java.util.Optional<OrderItem> findReviewItemForUpdate(Long order, Long item);
    @Query("SELECT i.order.id FROM OrderItem i WHERE i.id=:item AND i.order.customerId=:actor")
    java.util.Optional<Long> findOwnedReviewOrder(Long actor, Long item);
    String FIELDS = "id, order_id AS orderId, product_id AS productId, product_variant_id AS productVariantId, product_name_snapshot AS productNameSnapshot, variant_sku_snapshot AS variantSkuSnapshot, CAST(variant_options_snapshot AS TEXT) AS optionsJson, quantity, unit_price_snapshot AS unitPriceSnapshot, line_total AS lineTotal, original_unit_price_snapshot AS originalUnitPriceSnapshot, direct_unit_discount_snapshot AS directUnitDiscountSnapshot, direct_sale_percentage_snapshot AS directSalePercentageSnapshot, direct_sale_starts_at_snapshot AS directSaleStartsAtSnapshot, direct_sale_ends_at_snapshot AS directSaleEndsAtSnapshot, pricing_instant_snapshot AS pricingInstantSnapshot";
    @Query(value = "SELECT " + FIELDS + ", row_count AS rowCount FROM (SELECT i.*, COUNT(*) OVER(PARTITION BY order_id) AS row_count, ROW_NUMBER() OVER(PARTITION BY order_id ORDER BY id ASC) AS row_rank FROM order_items i WHERE order_id IN (:ids)) ranked WHERE row_rank <= 3 ORDER BY order_id,id", nativeQuery = true)
    List<ItemView> findPreviews(@Param("ids") Collection<Long> ids);

    @Query(value = "SELECT " + FIELDS + ", COUNT(*) OVER() AS rowCount FROM order_items WHERE order_id=:id ORDER BY id ASC", nativeQuery = true)
    List<ItemView> findDetails(@Param("id") Long id);

    interface ItemView {
        Long getId();
        Long getOrderId();
        UUID getProductId();
        UUID getProductVariantId();
        String getProductNameSnapshot();
        String getVariantSkuSnapshot();
        String getOptionsJson();
        Integer getQuantity();
        BigDecimal getUnitPriceSnapshot();
        BigDecimal getLineTotal();
        Long getRowCount();
        BigDecimal getOriginalUnitPriceSnapshot();
        BigDecimal getDirectUnitDiscountSnapshot();
        BigDecimal getDirectSalePercentageSnapshot();
        java.time.Instant getDirectSaleStartsAtSnapshot();
        java.time.Instant getDirectSaleEndsAtSnapshot();
        java.time.Instant getPricingInstantSnapshot();

    }
}
