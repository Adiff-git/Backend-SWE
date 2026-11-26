package com.example.demo.cart.service;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.cart.entity.Cart;
import com.example.demo.cart.entity.CartItem;
import com.example.demo.catalog.dto.response.CartVariantState;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public interface CartPreviewService { CartResponse getCartResponse(Cart cart, List<CartItem> rows, Map<UUID,CartVariantState> states, boolean rejectInvalid);
    CartResponse getCartResponse(Cart cart,List<CartItem> rows,Map<UUID,CartVariantState> states,boolean rejectInvalid,java.time.Instant at);
}
