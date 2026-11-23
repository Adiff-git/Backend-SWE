package com.example.demo.catalog.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.exception.AdminCatalogException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

class AdminCatalogRequestReaderTest {
    private final AdminCatalogRequestReader reader = new AdminCatalogRequestReader();
    private JsonNode object(String body) { return reader.object(body.getBytes(StandardCharsets.UTF_8), Set.of("name", "description", "brandId", "expectedBrandVersion", "sellingPrice", "specifications", "options"), Set.of()); }

    @ParameterizedTest
    @ValueSource(strings = {"{\"unknown\":1}", "{\"name\":\"a\",\"name\":\"b\"}", "{\"specifications\":{\"x\":1,\"x\":2}}", "{}{}", "[]", "null"})
    void object_shouldRejectUnknownDuplicateNestedOrTrailingData(String body) {
        assertThatThrownBy(() -> object(body)).isInstanceOf(AdminCatalogException.class);
    }

    @Test
    void presence_shouldDistinguishOmittedNullEmptyAndExplicitValues() {
        JsonNode body = object("{\"description\":null,\"brandId\":null}");
        assertThat(reader.text(body, "name", false, true).isSupplied()).isFalse();
        assertThat(reader.text(body, "description", true, false)).isEqualTo(CatalogField.supplied(null));
        assertThat(reader.identifier(body, "brandId", true)).isEqualTo(CatalogField.supplied(null));
        assertThat(reader.text(object("{\"description\":\"\"}"), "description", true, false).getValue()).isEmpty();
        assertThatThrownBy(() -> reader.text(object("{\"name\":null}"), "name", false, true)).isInstanceOf(AdminCatalogException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "01", "+1", "1.0", "9223372036854775808", ""})
    void revision_shouldRejectNoncanonicalOrOverflowTokens(String value) {
        assertThatThrownBy(() -> reader.revision(value)).isInstanceOf(AdminCatalogException.class);
    }

    @Test
    void patch_shouldRejectRevisionOnlyAndValidateCurrentTokenShape() {
        assertThatThrownBy(() -> reader.patch("{\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"0\"}".getBytes(StandardCharsets.UTF_8), Set.of("name")))
                .isInstanceOf(AdminCatalogException.class);
        assertThat(reader.revision("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void money_shouldRemainLosslessBeyondLong_andRejectFractionsAndCurrencyWithoutRounding() {
        BigInteger price = BigInteger.TEN.pow(2000).add(BigInteger.ONE);
        assertThat(reader.price(object("{\"sellingPrice\":{\"amount\":" + price + ",\"currency\":\"VND\"}}").get("sellingPrice")).getAmount()).isEqualTo(price);
        for (String body : List.of("{\"amount\":1.5,\"currency\":\"VND\"}", "{\"amount\":1,\"currency\":\"USD\"}")) {
            assertThatThrownBy(() -> reader.price(object("{\"sellingPrice\":" + body + "}").get("sellingPrice")))
                    .isInstanceOfSatisfying(AdminCatalogException.class, failure -> assertThat(failure.getStatus()).isEqualTo(422));
        }
        assertThatThrownBy(() -> reader.price(object("{\"sellingPrice\":{\"amount\":\"1\",\"currency\":\"VND\"}}").get("sellingPrice")))
                .isInstanceOfSatisfying(AdminCatalogException.class, failure -> assertThat(failure.getStatus()).isEqualTo(400));
    }

    @Test
    void query_shouldAllowRepeatedStatusesOnly_andBoundPageSize() {
        Map<String, List<String>> query = reader.query(Map.of("status", new String[] {"DRAFT", "DRAFT"}), Set.of("status"), Set.of("status"));
        assertThat(query.get("status")).containsExactly("DRAFT");
        assertThatThrownBy(() -> reader.query(Map.of("page", new String[] {"0", "1"}), Set.of("page"), Set.of())).isInstanceOf(AdminCatalogException.class);
        assertThat(reader.page(Map.of()).getPageSize()).isEqualTo(20);
        assertThatThrownBy(() -> reader.page(Map.of("size", List.of("101")))).isInstanceOf(AdminCatalogException.class);
    }

    @Test
    void facts_shouldPreserveEmptyFalseZero_andRejectNull() {
        JsonNode node = object("{\"specifications\":{\"schema_version\":1,\"values\":{\"flag\":false,\"count\":0}}}");
        Map<?, ?> facts = (Map<?, ?>) reader.specifications(node.get("specifications")).get("values");
        assertThat(facts.get("flag")).isEqualTo(false);
        assertThat(facts.get("count")).isEqualTo(BigInteger.ZERO);
        assertThatThrownBy(() -> reader.specifications(object("{\"specifications\":null}").get("specifications")))
                .isInstanceOf(AdminCatalogException.class);
    }

    @Test
    void brand_shouldRejectOrphanVersion_andRequireTokenWhenActuallyAssigning() {
        assertThatThrownBy(() -> reader.brandPrecondition(object("{\"brandId\":null,\"expectedBrandVersion\":\"0\"}"), false))
                .isInstanceOf(AdminCatalogException.class);
        JsonNode chosen = object("{\"brandId\":\"00000000-0000-0000-0000-000000000001\"}");
        reader.brandPrecondition(chosen, false); // Unchanged reference: service compares under the aggregate lock.
        assertThatThrownBy(() -> reader.brandPrecondition(chosen, true)).isInstanceOf(AdminCatalogException.class);
    }
}
