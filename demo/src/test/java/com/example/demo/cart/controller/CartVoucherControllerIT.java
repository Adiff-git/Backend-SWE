package com.example.demo.cart.controller;
import com.example.demo.config.CheckoutIntegrationSupport;
import com.example.demo.fixture.voucher.VoucherFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@AutoConfigureMockMvc
class CartVoucherControllerIT extends CheckoutIntegrationSupport {
    @Autowired MockMvc mvc;
    @Test void applyAndRemove_shouldUseOnlyOwnedGrantAndReturnCompletePrivateCart() throws Exception {
        long grant = transactions.execute(tx -> {
            long voucher = VoucherFixtures.definition(jdbc, "HTTP_" + System.nanoTime(), "FIXED_AMOUNT", BigDecimal.TEN, null, null);
            long usage = VoucherFixtures.usage(jdbc, voucher, fixture.owner());
            return VoucherFixtures.grant(jdbc, voucher, fixture.owner(), usage, Instant.now().minusSeconds(1));
        });
        mvc.perform(put("/api/me/cart/voucher").with(jwt().jwt(j -> j.subject(fixture.foreign().toString())).authorities(new SimpleGrantedAuthority("MANAGE_CART")))
            .contentType("application/json").content("{\"walletEntitlementId\":" + grant + "}"))
            .andExpect(status().isNotFound());
        mvc.perform(put("/api/me/cart/voucher").with(jwt().jwt(j -> j.subject(fixture.owner().toString())).authorities(new SimpleGrantedAuthority("MANAGE_CART")))
            .contentType("application/json").content("{\"walletEntitlementId\":" + grant + "}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(jsonPath("$.voucher.walletEntitlementId").value(grant)).andExpect(jsonPath("$.items").isArray());
        mvc.perform(delete("/api/me/cart/voucher").with(jwt().jwt(j -> j.subject(fixture.owner().toString())).authorities(new SimpleGrantedAuthority("MANAGE_CART"))))
            .andExpect(status().isNoContent());
    }
}
