package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.dto.response.OptionDimension;
import com.example.demo.catalog.dto.response.OptionValue;
import com.example.demo.catalog.entity.ProductVariant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class ProductOptionServiceImplTest {
    private final ProductOptionService service = new ProductOptionServiceImpl();

    @Test
    void getDimensions_shouldOrderCodePointsAndKeepLowestUuidPresentationLabels() {
        ProductVariant first = variant("00000000-0000-0000-0000-000000000001", Map.of("color",
                Map.of("label", "Color", "value", "red", "valueLabel", "Red")));
        ProductVariant second = variant("ffffffff-ffff-ffff-ffff-ffffffffffff", Map.of("color",
                Map.of("label", "COLOR", "value", "red", "valueLabel", "RED")));
        List<OptionDimension> dimensions = service.getDimensions(List.of(second, first));
        assertThat(dimensions).hasSize(1);
        assertThat(dimensions.getFirst().getLabel()).isEqualTo("Color");
        assertThat(dimensions.getFirst().getValues()).containsExactly(new OptionValue("red", "Red"));
        assertThat(first.getSelectionOptions()).containsExactlyEntriesOf(Map.of("color", "red"));
    }

    @Test
    void getDimensions_shouldSortSupplementaryCodePointsAfterBmpRatherThanUtf16Units() {
        ProductVariant variant = variant("00000000-0000-0000-0000-000000000001", Map.of(
                "\uE000", Map.of("label", "BMP", "value", "one", "valueLabel", "One"),
                "\uD800\uDC00", Map.of("label", "Supplementary", "value", "one", "valueLabel", "One")));
        assertThat(service.getDimensions(List.of(variant))).extracting(OptionDimension::getKey)
                .containsExactly("\uE000", "\uD800\uDC00");
    }

    @Test
    void getDimensions_shouldKeepDefaultVariantWithoutSyntheticDimensions() {
        ProductVariant variant = variant("00000000-0000-0000-0000-000000000001", Map.of());
        assertThat(service.getDimensions(List.of(variant))).isEmpty();
        assertThat(variant.getSelectionOptions()).isEmpty();
    }

    private ProductVariant variant(String id, Map<String, Map<String, String>> labels) {
        ProductVariant variant = BeanUtils.instantiateClass(ProductVariant.class);
        ReflectionTestUtils.setField(variant, "id", UUID.fromString(id));
        variant.setCanonicalOptionLabels(labels);
        return variant;
    }
}
