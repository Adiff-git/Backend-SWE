package com.example.demo.catalog.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.dto.request.ProductSearchRequest;
import com.example.demo.catalog.dto.request.ProductSearchRequest.BooleanValue;
import com.example.demo.catalog.dto.request.ProductSearchRequest.NumericValue;
import com.example.demo.catalog.dto.request.ProductSearchRequest.SpecificationAlternative;
import com.example.demo.catalog.dto.request.ProductSearchRequest.SpecificationGroup;
import com.example.demo.catalog.dto.request.ProductSearchRequest.SpecificationOperator;
import com.example.demo.catalog.dto.request.ProductSearchRequest.TextValue;
import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.ProductSearchSort;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class ProductSearchRequestTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    private static final String UUID = "00000000-0000-0000-0000-000000000001";

    @AfterAll
    static void closeFactory() { FACTORY.close(); }

    @Test
    void omittedValuesKeepLegacyDefaultsButExplicitNullIsInvalid() {
        ProductSearchRequest request = new ProductSearchRequest();
        assertThat(request.getPage()).isZero();
        assertThat(request.getSize()).isEqualTo(20);
        assertThat(VALIDATOR.validate(request)).isEmpty();
        request.setQ(null);
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
        request.setQ("null");
        assertThat(VALIDATOR.validate(request)).isEmpty();
        request.setMinPrice(null);
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
        request.setMinPrice(BigInteger.ZERO);
        request.setSize(null);
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void pageSizeAndKeywordBoundariesAreValidated() {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setPage(Integer.MAX_VALUE);
        request.setSize(100);
        request.setQ("𐐀".repeat(256));
        assertThat(VALIDATOR.validate(request)).isEmpty();
        request.setQ("𐐀".repeat(257));
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
        for (String keyword : List.of("sony ".repeat(17), "***", "sony\u0000")) {
            request.setQ(keyword);
            assertThat(VALIDATOR.validate(request)).isNotEmpty();
        }
        request.setQ(" \t");
        request.setPage(-1);
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
        request.setPage(0);
        for (int size : List.of(0, 101)) {
            request.setSize(size);
            assertThat(VALIDATOR.validate(request)).isNotEmpty();
        }
    }

    @Test
    void uuidAndRawCollectionBoundsArePreservedBeforeDeduplication() {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setCategoryId(UUID);
        request.setBrandIds(Collections.nCopies(20, UUID));
        assertThat(VALIDATOR.validate(request)).isEmpty();
        request.setBrandIds(Collections.nCopies(21, UUID));
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
        request.setBrandIds(List.of());
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
        request.setBrandIds(List.of(UUID.toUpperCase().replace("000000000001", "ABCDEF000001")));
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
        request.setBrandIds(List.of(UUID));
        request.setCategoryId("not-a-uuid");
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void exactPriceAmountsHaveThirtyDigitBoundWithoutDoubleCoercion() {
        ProductSearchRequest request = new ProductSearchRequest();
        BigInteger maximum = new BigInteger("9".repeat(30));
        request.setMinPrice(BigInteger.ZERO);
        request.setMaxPrice(maximum);
        assertThat(VALIDATOR.validate(request)).isEmpty();
        assertThat(request.getMaxPrice()).isEqualTo(maximum);
        request.setMaxPrice(BigInteger.TEN.pow(30));
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
        request.setMaxPrice(BigInteger.valueOf(-1));
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void nestedGroupsAndAlternativesEnforceShapeAndLimits() {
        ProductSearchRequest request = new ProductSearchRequest();
        SpecificationAlternative value = numeric("16.000000000001");
        SpecificationGroup group = group("synthetic_quantity", List.of(value));
        request.setSpecificationGroups(List.of(group));
        assertThat(VALIDATOR.validate(request)).isEmpty();
        group.setAlternatives(Collections.nCopies(4, value));
        request.setSpecificationGroups(Collections.nCopies(8, group));
        assertThat(VALIDATOR.validate(request)).isEmpty();
        request.setSpecificationGroups(Collections.nCopies(9, group));
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
        request.setSpecificationGroups(List.of(group));
        group.setAlternatives(Collections.nCopies(5, value));
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
        group.setAlternatives(List.of(value));
        group.setKey("a".repeat(65));
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
        group.setKey("Bad-Key");
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void numericOperandsAreExactAndPrecisionBoundedIncludingExponentNotation() {
        NumericValue precise = new NumericValue(new BigDecimal("123456789012345678.123456789012"));
        assertThat(VALIDATOR.validate(precise)).isEmpty();
        assertThat(precise.getAmount().toPlainString()).isEqualTo("123456789012345678.123456789012");
        assertThat(VALIDATOR.validate(new NumericValue(new BigDecimal("1e30")))).isEmpty();
        assertThat(VALIDATOR.validate(new NumericValue(new BigDecimal("9".repeat(31))))).isNotEmpty();
        assertThat(VALIDATOR.validate(new NumericValue(new BigDecimal("0.0000000000001")))).isNotEmpty();
        assertThat(VALIDATOR.validate(new NumericValue(null))).isNotEmpty();
    }

    @Test
    void rangesAndExplicitNullOperandMembersKeepTheirPresenceSemantics() {
        SpecificationAlternative range = new SpecificationAlternative();
        range.setOp(SpecificationOperator.BETWEEN);
        range.setMin(new BigDecimal("0.5"));
        range.setMax(new BigDecimal("16"));
        assertThat(VALIDATOR.validate(range)).isEmpty();
        range.setValue(null);
        assertThat(VALIDATOR.validate(range)).isNotEmpty();
        SpecificationAlternative comparator = numeric("16");
        comparator.setUnit(null);
        assertThat(VALIDATOR.validate(comparator)).isNotEmpty();
        comparator.setUnit("GB");
        assertThat(VALIDATOR.validate(comparator)).isEmpty();
        comparator.setMin(BigDecimal.ZERO);
        assertThat(VALIDATOR.validate(comparator)).isNotEmpty();
        range.setMax(new BigDecimal("0.0000000000001"));
        assertThat(VALIDATOR.validate(range)).isNotEmpty();
    }

    @Test
    void textBooleanAndDecodedJsonHaveDocumentedBounds() {
        assertThat(VALIDATOR.validate(new TextValue("a".repeat(128)))).isEmpty();
        assertThat(VALIDATOR.validate(new TextValue("a".repeat(129)))).isNotEmpty();
        assertThat(VALIDATOR.validate(new TextValue("\u0085\u00a0"))).isNotEmpty();
        assertThat(VALIDATOR.validate(new BooleanValue(false))).isEmpty();
        assertThat(VALIDATOR.validate(new BooleanValue(null))).isNotEmpty();
        ProductSearchRequest request = new ProductSearchRequest();
        request.setSpecFilters("𐐀".repeat(4096));
        assertThat(VALIDATOR.validate(request)).isEmpty();
        request.setSpecFilters("𐐀".repeat(4097));
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
        request.setSpecFilters("");
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void fixedSortValuesAndCriteriaRetainExactAmounts() {
        assertThat(ProductSearchSort.values()).extracting(ProductSearchSort::getParameterValue)
                .containsExactly("relevance", "price-asc", "price-desc", "name-asc", "name-desc");
        BigInteger price = new BigInteger("123456789012345678901234567890");
        ProductSearchCriteria criteria = ProductSearchCriteria.builder().minPrice(price).build();
        assertThat(criteria.getMinPrice()).isEqualTo(price);
        assertThat(criteria.getSize()).isEqualTo(20);
        // Raw lexical checks remain mandatory BEFORE conversion by T012, not a parser added here.
        assertThat("01".matches(ProductSearchRequest.CANONICAL_UNSIGNED_INTEGER)).isFalse();
        assertThat("1e3".matches(ProductSearchRequest.CANONICAL_PRICE)).isFalse();
        assertThat("1.0".matches(ProductSearchRequest.CANONICAL_PRICE)).isFalse();
    }

    private SpecificationAlternative numeric(String value) {
        SpecificationAlternative alternative = new SpecificationAlternative();
        alternative.setOp(SpecificationOperator.GTE);
        alternative.setValue(new NumericValue(new BigDecimal(value)));
        return alternative;
    }

    private SpecificationGroup group(String key, List<SpecificationAlternative> alternatives) {
        SpecificationGroup group = new SpecificationGroup();
        group.setKey(key);
        group.setAlternatives(alternatives);
        return group;
    }
}
