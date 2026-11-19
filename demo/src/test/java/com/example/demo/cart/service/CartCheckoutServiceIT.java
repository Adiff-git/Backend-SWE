package com.example.demo.cart.service;

import com.example.demo.cart.dto.response.CheckoutCartSeed;
import com.example.demo.config.CheckoutIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartCheckoutServiceIT extends CheckoutIntegrationSupport {
    @Autowired CartCheckoutService service;

    @Test void consumeCheckoutItems_shouldRemoveOnlyCapturedSelectedRows() {
        transactions.executeWithoutResult(s -> {
            CheckoutCartSeed seed = service.getCheckoutCartSeed(fixture.owner());
            assertThat(seed.getRows()).hasSize(1);
            service.consumeCheckoutItems(fixture.owner(),seed,seed.getRows());
        });
        assertThat(jdbc.queryForList("SELECT id FROM cart_items WHERE cart_id=?",Long.class,fixture.cart())).containsExactly(fixture.unselected());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts",Integer.class)).isEqualTo(1);
    }

    @Test void getCheckoutCartSeed_shouldNotCreateMissingCart() {
        java.util.List<CheckoutCartSeed.Row> rows = transactions.execute(s -> service.getCheckoutCartSeed(fixture.foreign()).getRows());
        assertThat(rows).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts",Integer.class)).isEqualTo(1);
    }

    @Test void consumeCheckoutItems_shouldRejectChangedQuantityAndRollBackWholeOperation() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            CheckoutCartSeed seed = service.getCheckoutCartSeed(fixture.owner());
            jdbc.update("UPDATE cart_items SET quantity=3 WHERE id=?",fixture.item());
            service.consumeCheckoutItems(fixture.owner(),seed,seed.getRows());
        })).isInstanceOf(com.example.demo.cart.exception.CartConflictException.class);
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id=?",Integer.class,fixture.item())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class)).isEqualTo(2);
    }
}
