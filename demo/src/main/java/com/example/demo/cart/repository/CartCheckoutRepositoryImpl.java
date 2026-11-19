package com.example.demo.cart.repository;

import com.example.demo.cart.dto.response.CheckoutCartSeed;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CartCheckoutRepositoryImpl implements CartCheckoutRepository {
    private final EntityManager entities;

    @Override
    public int deleteCheckoutRows(Long owner, Long cartId, List<CheckoutCartSeed.Row> rows) {
        if (rows.isEmpty()) return 0;
        String expected = IntStream.range(0, rows.size())
                .mapToObj(i -> "(:id" + i + ",:variant" + i + ",:quantity" + i + ")")
                .collect(Collectors.joining(","));
        Query query = entities.createNativeQuery("DELETE FROM cart_items i USING (VALUES " + expected
                + ") AS expected(id,variant,quantity) WHERE i.id=expected.id AND i.product_variant_id=expected.variant"
                + " AND i.quantity=expected.quantity AND i.selected=TRUE AND i.cart_id=:cart"
                + " AND EXISTS (SELECT 1 FROM carts c WHERE c.id=i.cart_id AND c.user_id=:owner)");
        query.setParameter("cart",cartId).setParameter("owner",owner);
        for (int i = 0; i < rows.size(); i++) {
            query.setParameter("id"+i,rows.get(i).getId());
            query.setParameter("variant"+i,rows.get(i).getVariantId());
            query.setParameter("quantity"+i,rows.get(i).getQuantity());
        }
        return query.executeUpdate();
    }
}
