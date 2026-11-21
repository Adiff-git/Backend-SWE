package com.example.demo.voucher.controller;
import com.example.demo.config.CheckoutIntegrationSupport;
import com.example.demo.fixture.voucher.VoucherFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
@AutoConfigureMockMvc
class VoucherControllerIT extends CheckoutIntegrationSupport {
    @Autowired MockMvc mvc;
    @Test void wallet_shouldReturnOnlyCurrentIdentityGrantsWithoutAdditionalPermission() throws Exception {
        long grant=transactions.execute(tx -> {
            long voucher=VoucherFixtures.definition(jdbc,"HTTP_WALLET","FIXED_AMOUNT",BigDecimal.ONE,null,null);
            long usage=VoucherFixtures.usage(jdbc,voucher,fixture.owner());
            return VoucherFixtures.grant(jdbc,voucher,fixture.owner(),usage,Instant.now().minusSeconds(1));
        });
        mvc.perform(get("/api/me/vouchers").with(jwt().jwt(j -> j.subject(fixture.owner().toString())).authorities(java.util.List.of())))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
            .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].walletEntitlementId").value(grant));
        mvc.perform(get("/api/me/vouchers/"+grant).with(jwt().jwt(j -> j.subject(fixture.foreign().toString())))).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/vouchers/"+grant).with(jwt().jwt(j -> j.subject(fixture.owner().toString()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.entitlementState").value("AVAILABLE"));
    }
}
