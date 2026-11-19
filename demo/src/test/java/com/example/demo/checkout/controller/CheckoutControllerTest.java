package com.example.demo.checkout.controller;

import com.example.demo.auth.config.AuthSecurityConfiguration;
import com.example.demo.auth.config.JwtProperties;
import com.example.demo.auth.security.ApiAccessDeniedHandler;
import com.example.demo.auth.security.ApiAuthenticationEntryPoint;
import com.example.demo.auth.security.DomainUserDetailsService;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.checkout.service.CheckoutService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(CheckoutController.class)
@Import({AuthSecurityConfiguration.class, JwtProperties.class, DomainUserDetailsService.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ApiErrorFactory.class, CheckoutRequestReader.class})
@TestPropertySource(properties = {"security.jwt.secret=controlled-test-key-with-at-least-32-bytes", "security.jwt.issuer=urn:test"})
class CheckoutControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CheckoutService service;
    @MockitoBean UserService users;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean CurrentUserIdProvider identity;

    @BeforeEach void prepare() { when(identity.getCurrentUserId()).thenReturn(1L); }

    private MockHttpServletRequestBuilder request() {
        return post("/api/me/cart/checkout").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CHECKOUT")))
                .header("Idempotency-Key", "Checkout_key_1234").contentType("application/json");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{}", "{\"addressId\":null}", "{\"addressId\":0}",
            "{\"addressId\":-1}", "{\"addressId\":\"1\"}", "{\"addressId\":true}", "{\"addressId\":[]}",
            "{\"addressId\":1.0}", "{\"addressId\":1e0}", "{\"addressId\":9223372036854775808}",
            "{\"addressId\":1,\"addressId\":1}", "{\"addressId\":1,\"userId\":2}", "{\"addressId\":1} {}"})
    void checkout_shouldRejectInvalidShapeWithoutServiceCall(String body) throws Exception {
        mvc.perform(request().content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"short", "123456789012345", "has spaces key___", "éxample_key_12345", "invalid+key_12345"})
    void checkout_shouldRejectInvalidKey(String key) throws Exception {
        mvc.perform(post("/api/me/cart/checkout").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CHECKOUT")))
                .contentType("application/json").header("Idempotency-Key",key).content("{\"addressId\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void checkout_shouldEnforceCurrentGrantBeforeBinding() throws Exception {
        mvc.perform(post("/api/me/cart/checkout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/cart/checkout").with(jwt())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void checkout_shouldRejectUnsupportedMediaAndQuery() throws Exception {
        mvc.perform(request().contentType("text/plain").content("{\"addressId\":1}")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(request().queryParam("userId","2").content("{\"addressId\":1}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private void successfulReceipt() {
        String body = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(
                com.example.demo.testdata.checkout.CheckoutTestData.receipt(5L,7L,com.example.demo.fixture.checkout.CheckoutFixtures.PLACED_AT));
        when(service.checkout(eq(1L),any(),any())).thenReturn(new com.example.demo.checkout.converter.CheckoutConverter().decode(body));
    }

    @ParameterizedTest @ValueSource(ints = {16,128})
    void checkout_shouldAcceptKeyAndAddressBoundaries(int length) throws Exception {
        successfulReceipt();
        mvc.perform(post("/api/me/cart/checkout").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CHECKOUT")))
                .header("Idempotency-Key","A".repeat(length)).contentType("application/vnd.checkout+json")
                .content("{\"addressId\":9223372036854775807}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location","/api/me/orders/5"))
                .andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(jsonPath("$.order.status").value("PENDING"));
    }

    @Test void checkout_shouldRejectRepeatedMissingOrOverlongKeysAndMissingMedia() throws Exception {
        mvc.perform(request().header("Idempotency-Key","Another_key_12345").content("{\"addressId\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/cart/checkout").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CHECKOUT")))
                .contentType("application/json").content("{\"addressId\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/cart/checkout").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CHECKOUT")))
                .header("Idempotency-Key","A".repeat(129)).contentType("application/json").content("{\"addressId\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/cart/checkout").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CHECKOUT")))
                .header("Idempotency-Key","Valid_key_123456").content("{\"addressId\":1}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/me/cart/checkout").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CHECKOUT")))
                .header("Idempotency-Key","Valid_key_123456"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void checkout_shouldReturnSafeSkippedSelectionsWithPartialSuccess() throws Exception {
        successfulReceipt();
        com.example.demo.checkout.dto.response.CheckoutReceiptResponse original=service.checkout(1L,"Fixture_key_12345",new com.example.demo.checkout.dto.request.CheckoutRequest(1L));
        when(service.checkout(eq(1L),any(),any())).thenReturn(new com.example.demo.checkout.dto.response.CheckoutReceiptResponse(
                original.getOrder(),java.util.List.of(new com.example.demo.checkout.dto.response.SkippedCheckoutItem(9L,
                com.example.demo.checkout.dto.response.SkippedCheckoutItem.Reason.PRODUCT_NOT_FOR_SALE))));
        mvc.perform(request().content("{\"addressId\":1}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.skippedItems[0].cartItemId").value(9))
                .andExpect(jsonPath("$.skippedItems[0].reason").value("PRODUCT_NOT_FOR_SALE"))
                .andExpect(jsonPath("$.skippedItems[0].productName").doesNotExist());
    }

    @Test void checkout_shouldDistinguishNoEligibleFromInfrastructureFailure() throws Exception {
        when(service.checkout(eq(1L),any(),any())).thenThrow(new com.example.demo.checkout.exception.NoEligibleCheckoutItemsException(
                java.util.List.of(new com.example.demo.checkout.dto.response.SkippedCheckoutItem(9L,
                com.example.demo.checkout.dto.response.SkippedCheckoutItem.Reason.INVALID_PRICE))));
        mvc.perform(request().content("{\"addressId\":1}")).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("NO_ELIGIBLE_CHECKOUT_ITEMS"))
                .andExpect(jsonPath("$.skippedItems[0].reason").value("INVALID_PRICE"));
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("controlled private failure"))
                .when(service).checkout(eq(1L),any(),any());
        mvc.perform(request().content("{\"addressId\":1}")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.skippedItems").doesNotExist());
    }
}
