package com.example.demo.voucher.controller;
import com.example.demo.auth.config.AuthSecurityConfiguration;
import com.example.demo.auth.config.JwtProperties;
import com.example.demo.auth.security.ApiAccessDeniedHandler;
import com.example.demo.auth.security.ApiAuthenticationEntryPoint;
import com.example.demo.auth.security.DomainUserDetailsService;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.cart.service.CartVoucherService;
import com.example.demo.common.exception.ApiErrorFactory;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(VoucherController.class)
@Import({AuthSecurityConfiguration.class, JwtProperties.class, DomainUserDetailsService.class,
    ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ApiErrorFactory.class, VoucherRequestReader.class})
@TestPropertySource(properties={"security.jwt.secret=controlled-test-key-with-at-least-32-bytes", "security.jwt.issuer=urn:test"})
class VoucherControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CartVoucherService service;
    @MockitoBean UserService users;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean CurrentUserIdProvider identity;
    @BeforeEach void prepare() { when(identity.getCurrentUserId()).thenReturn(1L); }
    @ParameterizedTest @ValueSource(strings={"?page=-1","?page=1.0","?page=01","?page=2147483648","?size=0","?size=101","?page=0&page=1","?code=TEN","?userId=2"})
    void wallet_shouldRejectInvalidQueryBeforeService(String query) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me/vouchers"+query).with(jwt())).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void wallet_shouldRequireAuthenticationButNoDomainPermission() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me/vouchers")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me/vouchers").with(jwt().authorities(java.util.List.of())))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"));
        org.mockito.Mockito.verify(service).getWalletPage(1L,0,20);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","01","1.0","9223372036854775808","TEN"})
    void detail_shouldRejectNoncanonicalId(String id) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me/vouchers/"+id).with(jwt())).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void detail_shouldHideForeignAndRejectBodyAndQuery() throws Exception {
        when(service.getWalletEntitlement(1L,2L)).thenThrow(new com.example.demo.voucher.exception.VoucherNotFoundException());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me/vouchers/2").with(jwt())).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me/vouchers/2?code=X").with(jwt())).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me/vouchers").with(jwt()).content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(put("/api/me/vouchers").with(jwt()).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
    }
}
