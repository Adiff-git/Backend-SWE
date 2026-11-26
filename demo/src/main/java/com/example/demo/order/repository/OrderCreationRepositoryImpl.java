package com.example.demo.order.repository;

import com.example.demo.order.dto.request.CreateOrderCommand;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
public class OrderCreationRepositoryImpl implements OrderCreationRepository {
    private final EntityManager entities;
    private final JsonMapper json = JsonMapper.builder().build();

    @Override public Long nextOrderId() {
        return ((Number)entities.createNativeQuery("SELECT nextval(pg_get_serial_sequence('orders','id'))").getSingleResult()).longValue();
    }

    @Override public void insertPendingOrder(Long id, String number, CreateOrderCommand command) {
        entities.createNativeQuery("INSERT INTO orders(id,customer_id,order_number,placed_at,status,recipient_name_snapshot,recipient_phone_snapshot,delivery_address_snapshot,merchandise_total,payable_total,inventory_managed,voucher_discount,voucher_snapshot) VALUES (:id,:owner,:number,:at,'PENDING',:name,:phone,:address,:total,:payable,true,:discount,CAST(:voucher AS JSONB))")
                .setParameter("id",id).setParameter("owner",command.getCustomerId()).setParameter("number",number)
                .setParameter("at",command.getPlacedAt()).setParameter("name",command.getRecipientName())
                .setParameter("phone",command.getRecipientPhone()).setParameter("address",command.getDeliveryAddress())
                .setParameter("total",new BigDecimal(command.getMerchandiseTotal()))
                .setParameter("payable",new BigDecimal(command.getPayableTotal())).setParameter("discount",new BigDecimal(command.getVoucherDiscount()))
                .setParameter("voucher",command.getVoucherSnapshot()==null ? null : json.writeValueAsString(command.getVoucherSnapshot())).executeUpdate();
    }

    @Override public Long insertHistoricalItem(Long order, CreateOrderCommand.Item item) {
        var pricing = item.getPricing();
        return ((Number)entities.createNativeQuery("INSERT INTO order_items(order_id,product_id,product_variant_id,product_name_snapshot,variant_sku_snapshot,variant_options_snapshot,quantity,unit_price_snapshot,line_total,original_unit_price_snapshot,direct_unit_discount_snapshot,direct_sale_percentage_snapshot,direct_sale_starts_at_snapshot,direct_sale_ends_at_snapshot,pricing_instant_snapshot) VALUES (:order,:product,:variant,:name,:sku,CAST(:options AS JSONB),:quantity,:price,:line,:original,:directDiscount,:percentage,:starts,:ends,:pricedAt) RETURNING id")
                .setParameter("order",order).setParameter("product",item.getProductId()).setParameter("variant",item.getVariantId())
                .setParameter("name",item.getProductName()).setParameter("sku",item.getSku()).setParameter("options",json.writeValueAsString(item.getOptions()))
                .setParameter("quantity",item.getQuantity()).setParameter("price",new BigDecimal(item.getUnitPrice()))
                .setParameter("line",new BigDecimal(item.getLineTotal()))
                .setParameter("original",pricing == null ? null : new BigDecimal(pricing.getOriginalPrice().getAmount()))
                .setParameter("directDiscount",pricing == null ? null : new BigDecimal(pricing.getDirectUnitDiscount().getAmount()))
                .setParameter("percentage",pricing == null ? null : pricing.getSalePercentage())
                .setParameter("starts",pricing == null ? null : pricing.getSaleStartsAt())
                .setParameter("ends",pricing == null ? null : pricing.getSaleEndsAt())
                .setParameter("pricedAt",pricing == null ? null : pricing.getPricedAt()).getSingleResult()).longValue();
    }
}
