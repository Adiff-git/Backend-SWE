package com.example.demo.catalog.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.dto.request.ProductSearchRequest;
import com.example.demo.catalog.dto.request.ProductSearchRequest.NumericValue;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class ProductSearchParameterParserTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final ProductSearchParameterParser parser = new ProductSearchParameterParser(FACTORY.getValidator());
    private static final String UUID = "00000000-0000-0000-0000-000000000001";

    @AfterAll
    static void closeFactory() { FACTORY.close(); }

    @Test
    void legacyDefaultsAndRepeatedBrandsPreserveCompatibility() {
        ProductSearchRequest request = parser.parse(Map.of());
        assertThat(request.getPage()).isZero();
        assertThat(request.getSize()).isEqualTo(20);
        assertThat(request.getQ()).isNull();
        assertThat(parser.parse(Map.of("brandId", List.of(UUID, UUID))).getBrandIds()).containsExactly(UUID);
        assertThat(parser.parse(Map.of("q", List.of(" \t"))).getQ()).isEqualTo(" \t");
        assertThat(parser.parse(Map.of("q", List.of("null"))).getQ()).isEqualTo("null");
    }

    @Test
    void rejectsUnknownNullAndDuplicateSingletonParameters() {
        reject(Map.of("unknown", List.of("x")));
        reject(Map.of("page", List.of("0", "0")));
        reject(Map.of("q", List.of()));
        Map<String, List<String>> nulls = new HashMap<>();
        nulls.put("q", Collections.singletonList(null));
        reject(nulls);
        nulls.put("q", null);
        reject(nulls);
        nulls.clear(); nulls.put(null, List.of("x")); reject(nulls);
        reject(Map.of("brandId", List.of(UUID + "," + UUID)));
        reject(Map.of("brandId", Collections.nCopies(21, UUID)));
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(InvalidCatalogRequestException.class);
    }

    @Test
    void enforcesLexicalAndNumericBoundsBeforeConversion() {
        for (String value : List.of("", "01", "+1", "-1", "1.0", "1e2", "2147483648", "null")) {
            reject(Map.of("page", List.of(value)));
        }
        for (String value : List.of("", "00", "-1", "1.0", "1e3", "9".repeat(31))) {
            reject(Map.of("minPrice", List.of(value)));
        }
        reject(Map.of("minPrice", List.of("2"), "maxPrice", List.of("1")));
        reject(Map.of("size", List.of("0")));
        reject(Map.of("size", List.of("101")));
        reject(Map.of("categoryId", List.of("ABCDEF00-0000-0000-0000-000000000001")));
        assertThat(parser.parse(Map.of("page", List.of("2147483647"), "size", List.of("100"),
                "maxPrice", List.of("9".repeat(30)))).getMaxPrice().toString()).isEqualTo("9".repeat(30));
    }

    @Test
    void validatesKeywordsAndSortWithoutSelectingAnyResults() {
        for (String q : List.of("***", "sony ".repeat(17), "a".repeat(257), "sony\u0000")) {
            reject(Map.of("q", List.of(q)));
        }
        reject(Map.of("sort", List.of("relevance")));
        reject(Map.of("sort", List.of("RELEVANCE")));
        assertThat(parser.parse(Map.of("q", List.of("WH-1000XM5"), "sort", List.of("relevance")))
                .getSort()).isEqualTo(ProductSearchSort.RELEVANCE);
    }

    @Test
    void acceptsAllTypedOperatorShapesAndPreservesExactDecimals() {
        for (String op : List.of("eq", "gt", "gte", "lt", "lte")) {
            ProductSearchRequest request = specifications("[{\"key\":\"synthetic\",\"alternatives\":[{\"op\":\""
                    + op + "\",\"value\":123456789012345678.123456789012,\"unit\":\"GB\"}]}]");
            NumericValue value = (NumericValue) request.getSpecificationGroups().getFirst().getAlternatives().getFirst().getValue();
            assertThat(value.getAmount()).isEqualByComparingTo(new BigDecimal("123456789012345678.123456789012"));
        }
        for (String op : List.of("eq", "prefix", "contains")) {
            specifications("[{\"key\":\"synthetic\",\"alternatives\":[{\"op\":\"" + op + "\",\"value\":\"abc\"}]}]");
        }
        specifications("[{\"key\":\"synthetic\",\"alternatives\":[{\"op\":\"eq\",\"value\":false}]}]");
        specifications("[{\"key\":\"synthetic\",\"alternatives\":[{\"op\":\"between\",\"min\":0,\"max\":16,\"unit\":\"GB\"}]}]");
        specifications("[{\"key\":\"synthetic\",\"alternatives\":[{\"op\":\"eq\",\"value\":1e3}]}]");
    }

    @Test
    void rejectsDuplicateMembersAtEachLevelEvenWhenValuesAgree() {
        for (String raw : List.of(
                "[{\"key\":\"x\",\"key\":\"x\",\"alternatives\":[]}]",
                "[{\"key\":\"x\",\"alternatives\":[{\"op\":\"eq\",\"op\":\"eq\",\"value\":1}]}]",
                "[{\"key\":\"x\",\"alternatives\":[{\"op\":\"eq\",\"value\":1,\"value\":1}]}]",
                "[{\"key\":\"x\",\"alternatives\":[{\"op\":\"eq\",\"value\":{\"x\":1,\"x\":1}}]}]")) {
            invalidSpecifications(raw);
        }
    }

    @Test
    void rejectsMalformedStructuresUnknownFieldsAndExplicitNulls() {
        for (String raw : List.of("", "null", "[]", "{}", "[null]", "[] []", "[",
                "[{\"key\":null,\"alternatives\":[]}]",
                "[{\"key\":\"x\",\"alternatives\":[{\"op\":\"eq\",\"value\":null}]}]",
                "[{\"key\":\"x\",\"alternatives\":[{\"op\":\"eq\",\"value\":1,\"unit\":null}]}]",
                "[{\"key\":\"x\",\"extra\":1,\"alternatives\":[{\"op\":\"eq\",\"value\":1}]}]")) {
            invalidSpecifications(raw);
        }
        reject(Map.of("specFilters", List.of("[]")));
        invalidSpecifications(" ".repeat(4097));
    }

    @Test
    void rejectsBadOperatorShapesAndPrecisionWithoutRegistryApproval() {
        for (String alternative : List.of(
                "{\"op\":\"ne\",\"value\":1}", "{\"op\":\"eq\",\"value\":[]}",
                "{\"op\":\"gt\",\"value\":true}", "{\"op\":\"prefix\",\"value\":1}",
                "{\"op\":\"eq\",\"value\":true,\"unit\":\"GB\"}",
                "{\"op\":\"eq\",\"value\":\"text\",\"unit\":\"GB\"}",
                "{\"op\":\"between\",\"min\":2,\"max\":1}",
                "{\"op\":\"between\",\"min\":1,\"max\":2,\"value\":1}",
                "{\"op\":\"eq\",\"value\":1,\"min\":0}",
                "{\"op\":\"eq\",\"value\":0.0000000000001}",
                "{\"op\":\"eq\",\"value\":16.0000000000000}",
                "{\"op\":\"eq\",\"value\":" + "9".repeat(31) + "}")) {
            invalidSpecifications("[{\"key\":\"synthetic\",\"alternatives\":[" + alternative + "]}]");
        }
    }

    @Test
    void rawGroupAlternativeLimitsAndDuplicateKeysAreNotHiddenByDeduplication() {
        String group = "{\"key\":\"x\",\"alternatives\":[{\"op\":\"eq\",\"value\":1}]}";
        invalidSpecifications("[" + group + "," + group + "]");
        String alternatives = String.join(",", Collections.nCopies(5, "{\"op\":\"eq\",\"value\":1}"));
        invalidSpecifications("[{\"key\":\"x\",\"alternatives\":[" + alternatives + "]}]");
        String tooMany = "[" + String.join(",", java.util.stream.IntStream.range(0, 9)
                .mapToObj(i -> group.replace("\"x\"", "\"x" + i + "\"")).toList()) + "]";
        invalidSpecifications(tooMany);
    }

    @Test
    void decodesTransportExactlyOnceAndLeavesOtherMappersUnchanged() {
        String raw = "[{\"key\":\"synthetic\",\"alternatives\":[{\"op\":\"eq\",\"value\":\"USB+C\"}]}]";
        String encoded = java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8);
        String decoded = java.net.URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(specifications(decoded).getSpecificationGroups()).hasSize(1);
        invalidSpecifications(encoded); // Parser input is transport-decoded;it does not decode again.
        tools.jackson.databind.json.JsonMapper ordinary = tools.jackson.databind.json.JsonMapper.builder().build();
        assertThat(ordinary.readTree("{\"a\":1,\"a\":2}").get("a").asInt()).isEqualTo(2);
    }

    private ProductSearchRequest specifications(String raw) {
        return parser.parse(Map.of("categoryId", List.of(UUID), "specFilters", List.of(raw)));
    }

    private void invalidSpecifications(String raw) {
        assertThatThrownBy(() -> specifications(raw)).isInstanceOf(InvalidCatalogRequestException.class);
    }

    private void reject(Map<String, List<String>> parameters) {
        assertThatThrownBy(() -> parser.parse(parameters)).isInstanceOf(InvalidCatalogRequestException.class);
    }
}
