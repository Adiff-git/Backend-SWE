package com.example.demo.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.catalog.dto.request.ProductSearchRequest;
import com.example.demo.catalog.dto.response.CategorySummary;
import com.example.demo.catalog.dto.response.ProductListingItem;
import com.example.demo.catalog.dto.response.ProductPage;
import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.catalog.search.ProductSearchNormalizer;
import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.common.exception.ApiExceptionHandler;
import com.example.demo.common.security.CatalogSecurityConfiguration;
import com.example.demo.common.security.CatalogSecurityProblemHandler;
import java.math.BigInteger;
import java.text.Normalizer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP contract authoring only;the service is mocked,no search implementation or approved vocabulary. */
@WebMvcTest(controllers = ProductCatalogController.class, properties = "catalog.search.fuzzy.enabled=true")
@Import({CatalogSecurityConfiguration.class, CatalogSecurityProblemHandler.class,
        ApiExceptionHandler.class, com.example.demo.common.exception.ApiErrorFactory.class})
class ProductListingControllerTest {
    private static final String MODE = "X-Catalog-Search-Mode";
    @MockitoBean private com.example.demo.auth.security.CurrentStateJwtConverter catalogJwtConverter;
    @MockitoBean private org.springframework.security.oauth2.jwt.JwtDecoder catalogJwtDecoder;
    @MockitoBean private ProductCatalogService service;
    @Autowired private MockMvc mvc;
    @Autowired private Environment environment;

    @BeforeEach
    void prepareResponse() {
        when(service.listProducts(any(ProductSearchRequest.class))).thenReturn(new ProductPage(List.of(), 0, 20, 0, 0));
    }

    @Test
    void anonymousDefaultAndOutOfRangePagesUseCanonicalResponse() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        ProductSearchRequest request = capturedRequest();
        assertThat(request.getPage()).isZero();
        assertThat(request.getSize()).isEqualTo(20);
        when(service.listProducts(any(ProductSearchRequest.class))).thenReturn(new ProductPage(List.of(), 999, 1, 3, 3));
        mvc.perform(get("/api/products?page=999&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void acceptsSizeBoundaryValues() throws Exception {
        when(service.listProducts(any(ProductSearchRequest.class))).thenReturn(new ProductPage(List.of(), 0, 1, 0, 0));
        mvc.perform(get("/api/products?size=1")).andExpect(status().isOk());
        when(service.listProducts(any(ProductSearchRequest.class))).thenReturn(new ProductPage(List.of(), 0, 100, 0, 0));
        mvc.perform(get("/api/products?size=100")).andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidDuplicateAndUnapprovedParametersAsProblemDetail() throws Exception {
        for (String query : List.of("page=-1", "page=nope", "page=0&page=1", "size=0",
                "size=101", "size=nope", "size=1&size=2", "sort=name", "filter=status")) {
            mvc.perform(get("/api/products?" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").isNotEmpty());
        }
        verifyNoInteractions(service);
    }

    @Test
    void listingSerializesOnlyAdditiveMetadataIncludingPairedNulls() throws Exception {
        var category = new CategorySummary(UUID.randomUUID(), "Phones");
        var variantId = UUID.randomUUID();
        when(service.listProducts(any(ProductSearchRequest.class))).thenReturn(new ProductPage(List.of(
                new ProductListingItem(UUID.randomUUID(), "Phone", category,
                        null, new SellingPrice(BigInteger.TEN, "VND"), variantId),
                new ProductListingItem(UUID.randomUUID(), "Legacy", category, null, null, null)), 0, 20, 2, 1));
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].displayPrice.amount").value(10))
                .andExpect(jsonPath("$.items[0].displayPrice.currency").value("VND"))
                .andExpect(jsonPath("$.items[0].representativeVariantId").value(variantId.toString()))
                .andExpect(jsonPath("$.items[1].displayPrice").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[1].representativeVariantId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].rawRank").doesNotExist());
    }

    @Test
    void listProducts_shouldAcceptAnonymousKeywordAndRetainListingEnvelope() throws Exception {
        mvc.perform(get("/api/products").param("q", "máy tính"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        assertThat(capturedRequest().getQ()).isEqualTo("máy tính");
    }

    @Test
    void listProducts_shouldPreserveRawVietnameseQueryForSharedNormalizer() throws Exception {
        String raw = Normalizer.normalize("  ĐIỆN\u00a0THOẠI Điện  ", Normalizer.Form.NFD);
        mvc.perform(get("/api/products").param("q", raw)).andExpect(status().isOk());
        String forwarded = capturedRequest().getQ();
        assertThat(forwarded).isEqualTo(raw); // Controller must not lose original phrase/order.
        var query = new ProductSearchNormalizer().normalizeQuery(forwarded);
        assertThat(query.getOriginalNormalized()).isEqualTo("dien thoai dien");
        assertThat(query.getSequence()).extracting(ProductSearchNormalizer.Token::getText)
                .containsExactly("dien", "thoai", "dien");
        assertThat(query.getTokens()).extracting(ProductSearchNormalizer.Token::getText)
                .containsExactly("dien", "thoai");
    }

    @Test
    void listProducts_shouldPreserveStructuralIdentifiersAndNumericText() throws Exception {
        mvc.perform(get("/api/products").param("q", "WH-1000XM5 i7-1360P USB-C C++ 16.0"))
                .andExpect(status().isOk());
        var query = new ProductSearchNormalizer().normalizeQuery(capturedRequest().getQ());
        assertThat(query.getTokens()).extracting(ProductSearchNormalizer.Token::getText)
                .containsExactly("wh-1000xm5", "i7-1360p", "usb-c", "c++", "16.0");
        assertThat(query.getTokens()).allMatch(ProductSearchNormalizer.Token::isStructurallyProtected);
    }

    @Test
    void listProducts_shouldTreatBlankAsLegacyWithoutSearchModeHeader() throws Exception {
        for (String blank : List.of("", " \t\u00a0\u3000")) {
            mvc.perform(get("/api/products").param("q", blank))
                    .andExpect(status().isOk()).andExpect(header().doesNotExist(MODE));
        }
        mvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(header().doesNotExist(MODE));
    }

    @Test
    void listProducts_shouldRejectZeroTokensAndControlsBeforeCallingService() throws Exception {
        for (String raw : List.of("***", "---", "sony\u0000")) {
            assertSafeBadKeyword(raw);
        }
        verifyNoInteractions(service);
    }

    @Test
    void listProducts_shouldRejectDuplicateKeywordEvenWhenValuesEqual() throws Exception {
        for (String second : List.of("sony", "apple")) {
            mvc.perform(get("/api/products").param("q", "sony", second))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("about:blank"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").isNotEmpty());
        }
        verifyNoInteractions(service);
    }

    @Test
    void listProducts_shouldRejectRawLimitsBeforeNormalizationOrDeduplication() throws Exception {
        for (String raw : List.of("a".repeat(257), "e\u0302".repeat(129), "SONY ".repeat(17).trim())) {
            assertSafeBadKeyword(raw);
        }
        verifyNoInteractions(service);
    }

    @Test
    void listProducts_shouldAcceptUnicodeCodePointAndRawTokenBoundaries() throws Exception {
        // 256 astral letters are512 UTF16 code units;limits apply to code points.
        mvc.perform(get("/api/products").param("q", "\uD801\uDC00".repeat(256))).andExpect(status().isOk());
        mvc.perform(get("/api/products").param("q", "SONY ".repeat(16).trim())).andExpect(status().isOk());
    }

    @Test
    void listProducts_shouldTreatLiteralNullAsSearchText() throws Exception {
        mvc.perform(get("/api/products").param("q", "null")).andExpect(status().isOk());
        assertThat(capturedRequest().getQ()).isEqualTo("null");
    }

    @Test
    void listProducts_shouldReturnSafeParameterProblemFromService() throws Exception {
        when(service.listProducts(any(ProductSearchRequest.class)))
                .thenThrow(new InvalidCatalogRequestException("Invalid q parameter"));
        mvc.perform(get("/api/products").param("q", "private-query-sentinel"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Invalid q parameter"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-query-sentinel"))))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void listProducts_shouldRejectInvalidBearerBeforeKeywordService() throws Exception {
        when(catalogJwtDecoder.decode("invalid-contract-token")).thenThrow(new BadJwtException("test-only invalid token"));
        mvc.perform(get("/api/products").param("q", "sony").header("Authorization", "Bearer invalid-contract-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication is required"));
        verifyNoInteractions(service);
    }

    @Test
    void listProducts_shouldReportFuzzyEnabledModeForNonblankKeyword() throws Exception {
        assertThat(environment.getProperty("catalog.search.fuzzy.enabled", Boolean.class)).isTrue();
        // Intentionally RED until future keyword HTTP behavior emits the approved mode header.
        mvc.perform(get("/api/products").param("q", "sony"))
                .andExpect(status().isOk()).andExpect(header().string(MODE, "fuzzy-enabled"));
    }

    @Nested
    @TestPropertySource(properties = "catalog.search.fuzzy.enabled=false")
    class ExactOnlyMode {
        @Test
        void listProducts_shouldReportExplicitDiagnosticModeForNonblankKeyword() throws Exception {
            assertThat(environment.getProperty("catalog.search.fuzzy.enabled", Boolean.class)).isFalse();
            // Structural model syntax only;no vocabulary approval or fuzzy capability is fabricated.
            mvc.perform(get("/api/products").param("q", "WH-1000XM5"))
                    .andExpect(status().isOk()).andExpect(header().string(MODE, "exact-only"));
        }
    }

    private ProductSearchRequest capturedRequest() {
        var captor = ArgumentCaptor.forClass(ProductSearchRequest.class);
        verify(service).listProducts(captor.capture());
        return captor.getValue();
    }

    private void assertSafeBadKeyword(String raw) throws Exception {
        mvc.perform(get("/api/products").param("q", raw))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
}
