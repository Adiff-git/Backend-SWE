package com.example.demo.cart.controller;
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

@WebMvcTest(CartVoucherController.class)
@Import({AuthSecurityConfiguration.class, JwtProperties.class, DomainUserDetailsService.class,
    ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ApiErrorFactory.class, CartRequestReader.class})
@TestPropertySource(properties={"security.jwt.secret=controlled-test-key-with-at-least-32-bytes", "security.jwt.issuer=urn:test"})
class CartVoucherControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CartVoucherService service;
    @MockitoBean UserService users;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean CurrentUserIdProvider identity;
    @BeforeEach void prepare() { when(identity.getCurrentUserId()).thenReturn(1L); }
    @ParameterizedTest @ValueSource(strings={"", "null", "[]", "{}", "{\"walletEntitlementId\":null}",
        "{\"walletEntitlementId\":\"1\"}", "{\"walletEntitlementId\":0}", "{\"walletEntitlementId\":-1}",
        "{\"walletEntitlementId\":1.0}", "{\"walletEntitlementId\":1e0}", "{\"walletEntitlementId\":9223372036854775808}",
        "{\"walletEntitlementId\":1,\"walletEntitlementId\":2}", "{\"code\":\"TEN\"}",
        "{\"voucherId\":1}", "{\"walletEntitlementId\":1,\"userId\":2}", "{\"walletEntitlementId\":1} {}"})
    void applyVoucher_shouldRejectMalformedOrOverrideInputBeforeService(String body) throws Exception {
        mvc.perform(put("/api/me/cart/voucher").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART")))
            .contentType("application/json").content(body)).andExpect(status().isBadRequest())
            .andExpect(header().string("Cache-Control", "private, no-store"));
        verifyNoInteractions(service);
    }
    @Test void endpoints_shouldAuthenticateAndAuthorizeBeforeParsing() throws Exception {
        mvc.perform(put("/api/me/cart/voucher").contentType("application/json").content("bad")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me/cart/voucher").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CHECKOUT")))
            .contentType("application/json").content("bad")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void removeVoucher_shouldReturnPrivateNoContentAndRejectQueryOrBody() throws Exception {
        mvc.perform(delete("/api/me/cart/voucher").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART"))))
            .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "private, no-store"));
        mvc.perform(delete("/api/me/cart/voucher?owner=2").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART"))))
            .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/me/cart/voucher").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART"))).content("{}"))
            .andExpect(status().isBadRequest());
    }
    @Test void applyVoucher_shouldRejectUnsupportedMedia() throws Exception {
        mvc.perform(put("/api/me/cart/voucher").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART")))
            .contentType("text/plain").content("{}" )).andExpect(status().isUnsupportedMediaType());
    }

    @ParameterizedTest @ValueSource(strings={"application/json","application/vnd.checkout+json"})
    void applyVoucher_shouldAcceptCompatibleJsonTypes(String media) throws Exception {
        mvc.perform(put("/api/me/cart/voucher").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART")))
            .contentType(media).content("{\"walletEntitlementId\":1}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"));
        org.mockito.Mockito.verify(service).applyVoucher(1L,1L);
    }
    @Test void applyVoucher_shouldRejectAbsentBodyWithoutMediaHeaderAsBadRequest() throws Exception {
        mvc.perform(put("/api/me/cart/voucher").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART")))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
