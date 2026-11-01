package com.example.demo.wishlist.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.config.AuthSecurityConfiguration;
import com.example.demo.auth.config.JwtProperties;
import com.example.demo.auth.security.ApiAccessDeniedHandler;
import com.example.demo.auth.security.ApiAuthenticationEntryPoint;
import com.example.demo.auth.security.DomainUserDetailsService;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.catalog.dto.response.ProductSummary;
import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.exception.ProductNotFoundException;
import com.example.demo.common.exception.ApiErrorFactory;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.common.identity.IdentityUnavailableException;
import com.example.demo.user.service.UserService;
import com.example.demo.wishlist.dto.response.WishlistItemResponse;
import com.example.demo.wishlist.dto.response.WishlistPage;
import com.example.demo.wishlist.service.AddWishlistResult;
import com.example.demo.wishlist.service.WishlistService;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
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

@WebMvcTest(WishlistController.class)
@Import({AuthSecurityConfiguration.class, JwtProperties.class, DomainUserDetailsService.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ApiErrorFactory.class})
@TestPropertySource(properties = {"security.jwt.secret=controlled-test-key-with-at-least-32-bytes",
        "security.jwt.issuer=urn:test"})
class WishlistControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private WishlistService wishlist;
    @MockitoBean private UserService users;
    @MockitoBean private AuthSessionService sessions;
    @MockitoBean private CurrentUserIdProvider identity;
    private final UUID id = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @BeforeEach
    void prepare() {
        when(identity.getCurrentUserId()).thenReturn(1L);
    }

    @Test
    void addProduct_shouldReturnOneItemWithCorrectStatus_whenCreatedOrDuplicate() throws Exception {
        WishlistItemResponse item = new WishlistItemResponse(Instant.parse("2026-10-31T00:00:00Z"),
                new ProductSummary(id, "Product", null, null));
        for (boolean created : new boolean[] {true, false}) {
            when(wishlist.addProduct(1L, id)).thenReturn(new AddWishlistResult(created, item));
            mvc.perform(post("/api/me/wishlist/" + id).with(jwt().authorities(
                    new SimpleGrantedAuthority("MANAGE_WISHLIST"))))
                    .andExpect(status().is(created ? 201 : 200))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.product.id").value(id.toString()))
                    .andExpect(jsonPath("$.addedAt").value("2026-10-31T00:00:00Z"))
                    .andExpect(jsonPath("$.created").doesNotExist());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"userId\":2}", "[]", "not-json"})
    void addProduct_shouldRejectSuppliedBody_whenMutationIsBodyless(String body) throws Exception {
        mvc.perform(post("/api/me/wishlist/" + id).with(jwt().authorities(
                new SimpleGrantedAuthority("MANAGE_WISHLIST"))).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").exists());
        verifyNoInteractions(wishlist);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad", "1-1-1-1-1", "00000000-0000-4000-8000-00000000000A"})
    void addProduct_shouldRejectInvalidIdentifier_whenNotCanonical(String value) throws Exception {
        mvc.perform(post("/api/me/wishlist/" + value).with(jwt().authorities(
                new SimpleGrantedAuthority("MANAGE_WISHLIST"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(wishlist);
    }

    @Test
    void addProduct_shouldRejectOwnerSelectionAndMissingPermission_whenUnauthorized() throws Exception {
        mvc.perform(post("/api/me/wishlist/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/wishlist/" + id).with(jwt().authorities(
                new SimpleGrantedAuthority("MANAGE_ADDRESS"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/me/wishlist/" + id + "?userId=2").with(jwt().authorities(
                new SimpleGrantedAuthority("MANAGE_WISHLIST"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(wishlist);
    }

    @Test
    void addProduct_shouldPreserveProblemDetails_whenDomainFails() throws Exception {
        when(wishlist.addProduct(eq(1L), any())).thenThrow(new ProductNotFoundException());
        mvc.perform(post("/api/me/wishlist/" + id).with(jwt().authorities(
                new SimpleGrantedAuthority("MANAGE_WISHLIST"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.path").exists())
                .andExpect(jsonPath("$.timestamp").exists());
        when(wishlist.addProduct(eq(1L), any())).thenThrow(new CatalogStorageUnavailableException());
        mvc.perform(post("/api/me/wishlist/" + id).with(jwt().authorities(
                new SimpleGrantedAuthority("MANAGE_WISHLIST"))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void addProduct_shouldRetainBearerChallenge_whenCurrentUserDisappearsAfterAuthentication() throws Exception {
        when(wishlist.addProduct(eq(1L), any())).thenThrow(new IdentityUnavailableException());
        mvc.perform(post("/api/me/wishlist/" + id).with(jwt().authorities(
                new SimpleGrantedAuthority("MANAGE_WISHLIST"))))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void removeProduct_shouldReturnEmptySuccess_whenOwnedOrAbsent() throws Exception {
        mvc.perform(delete("/api/me/wishlist/" + id)
                .with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_WISHLIST"))))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
        verify(wishlist).removeProduct(1L, id);
    }

    @Test
    void removeProduct_shouldEnforcePermissionAndInputContract_whenRequestIsInvalid() throws Exception {
        mvc.perform(delete("/api/me/wishlist/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/me/wishlist/" + id).with(jwt())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/me/wishlist/bad")
                .with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_WISHLIST"))))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/me/wishlist/" + id + "?userId=2")
                .with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_WISHLIST"))))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/me/wishlist/" + id)
                .with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_WISHLIST")))
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(wishlist);
    }

    @Test
    void listWishlist_shouldUseDefaultsAndEmptyPageContract_whenParametersAreOmitted() throws Exception {
        when(wishlist.listWishlist(1L, 0, 20)).thenReturn(new WishlistPage(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/me/wishlist").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_WISHLIST"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=", "page=null", "page=00", "page=+1", "page=1.5",
            "page=2147483648", "size=0", "size=101", "size=", "size=1&size=2", "page=0&page=1",
            "sort=name", "userId=2", "size=%2020"})
    void listWishlist_shouldRejectAmbiguousOrInvalidInput_whenQueryIsInvalid(String query) throws Exception {
        mvc.perform(get("/api/me/wishlist?" + query)
                .with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_WISHLIST"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(wishlist);
    }

    @Test
    void listWishlist_shouldAcceptSizeBoundsAndBeyondLastPage_whenQueryIsValid() throws Exception {
        for (int size : List.of(1, 100)) {
            when(wishlist.listWishlist(1L, 999, size)).thenReturn(new WishlistPage(List.of(), 999, size, 2, 2));
            mvc.perform(get("/api/me/wishlist?page=999&size=" + size)
                    .with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_WISHLIST"))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.totalItems").value(2));
        }
    }
}
