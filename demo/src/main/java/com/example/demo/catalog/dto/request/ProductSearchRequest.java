package com.example.demo.catalog.dto.request;

import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.catalog.search.ProductSearchNormalizer;
import com.example.demo.catalog.search.ProductSearchSort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;

/** Parsed shape only. Raw lexical/duplicate checks belong to the future parameter parser. */
@Getter
@NoArgsConstructor
public class ProductSearchRequest {
    public static final String CANONICAL_UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    public static final String CANONICAL_UNSIGNED_INTEGER = "0|[1-9][0-9]*";
    public static final String CANONICAL_PRICE = "0|[1-9][0-9]{0,29}";

    private String q;
    @NotNull @Min(0) @Max(Integer.MAX_VALUE)
    private Integer page = 0;
    @NotNull @Min(1) @Max(100)
    private Integer size = 20;
    @Pattern(regexp = CANONICAL_UUID)
    private String categoryId;
    @Size(min = 1, max = 20)
    private List<@NotNull @Pattern(regexp = CANONICAL_UUID) String> brandIds;
    @PositiveOrZero @Digits(integer = 30, fraction = 0)
    private BigInteger minPrice;
    @PositiveOrZero @Digits(integer = 30, fraction = 0)
    private BigInteger maxPrice;
    private ProductSearchSort sort;
    private String specFilters;
    @Size(min = 1, max = 8)
    private List<@NotNull @Valid SpecificationGroup> specificationGroups;
    @Getter(AccessLevel.NONE)
    private final Set<String> explicitlyNullFields = new HashSet<>();

    public void setQ(String value) { supplied("q", value); q = value; }
    public void setPage(Integer value) { supplied("page", value); page = value; }
    public void setSize(Integer value) { supplied("size", value); size = value; }
    public void setCategoryId(String value) { supplied("categoryId", value); categoryId = value; }
    public void setBrandIds(List<String> value) { supplied("brandIds", value); brandIds = value; }
    public void setMinPrice(BigInteger value) { supplied("minPrice", value); minPrice = value; }
    public void setMaxPrice(BigInteger value) { supplied("maxPrice", value); maxPrice = value; }
    public void setSort(ProductSearchSort value) { supplied("sort", value); sort = value; }
    public void setSpecFilters(String value) { supplied("specFilters", value); specFilters = value; }
    public void setSpecificationGroups(List<SpecificationGroup> value) {
        supplied("specificationGroups", value);
        specificationGroups = value;
    }

    private void supplied(String field, Object value) {
        if (value == null) {
            explicitlyNullFields.add(field);
        } else {
            explicitlyNullFields.remove(field);
        }
    }

    @AssertTrue(message = "Explicit null is not an omitted search parameter")
    public boolean isSuppliedValuesNonNull() { return explicitlyNullFields.isEmpty(); }

    @AssertTrue(message = "Invalid keyword shape or limits")
    public boolean isKeywordShapeValid() {
        try {
            new ProductSearchNormalizer().normalizeQuery(q);
            return true;
        } catch (InvalidCatalogRequestException exception) {
            return false;
        }
    }

    @AssertTrue(message = "Decoded specFilters must contain 1 to 4096 code points")
    public boolean isSpecFiltersLengthValid() {
        return specFilters == null || !specFilters.isEmpty()
                && specFilters.codePointCount(0, specFilters.length()) <= 4096;
    }

    @AssertTrue(message = "At most 32 alternatives are supported")
    public boolean isTotalAlternativesValid() {
        if (specificationGroups == null) {
            return true;
        }
        long total = 0;
        for (SpecificationGroup group : specificationGroups) {
            if (group != null && group.getAlternatives() != null) {
                total += group.getAlternatives().size();
            }
        }
        return total <= 32;
    }

    @Getter @Setter @NoArgsConstructor
    public static class SpecificationGroup {
        @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z][a-z0-9_]*")
        private String key;
        @NotNull @Size(min = 1, max = 4)
        private List<@NotNull @Valid SpecificationAlternative> alternatives;
    }

    public enum SpecificationOperator { EQ, GT, GTE, LT, LTE, BETWEEN, PREFIX, CONTAINS }

    @Getter @Setter @NoArgsConstructor
    public static class SpecificationAlternative {
        @NotNull
        private SpecificationOperator op;
        @Valid
        private SpecificationValue value;
        private BigDecimal min;
        private BigDecimal max;
        @Size(min = 1, max = 32)
        private String unit;

        @Getter(AccessLevel.NONE)
        private final Set<String> explicitlyNullFields = new HashSet<>();

        public void setValue(SpecificationValue supplied) { trackNull("value", supplied); value = supplied; }
        public void setMin(BigDecimal supplied) { trackNull("min", supplied); min = supplied; }
        public void setMax(BigDecimal supplied) { trackNull("max", supplied); max = supplied; }
        public void setUnit(String supplied) { trackNull("unit", supplied); unit = supplied; }

        private void trackNull(String name, Object supplied) {
            if (supplied == null) { explicitlyNullFields.add(name); }
            else { explicitlyNullFields.remove(name); }
        }

        @AssertTrue(message = "Explicit null operand members are invalid")
        public boolean isSuppliedMembersNonNull() { return explicitlyNullFields.isEmpty(); }

        @AssertTrue(message = "Range numbers exceed precision/scale bounds")
        public boolean isNumericBoundsValid() {
            return decimalWithinBounds(min) && decimalWithinBounds(max);
        }

        @AssertTrue(message = "Range requires min/max only;other operators require value only")
        public boolean isOperandShapeValid() {
            if (op == null) {
                return true; // @NotNull reports this field.
            }
            return op == SpecificationOperator.BETWEEN ? value == null && min != null && max != null
                    : value != null && min == null && max == null;
        }
    }

    private static boolean decimalWithinBounds(BigDecimal amount) {
        return amount == null || amount.precision() <= 30 && amount.scale() <= 12;
    }

    public sealed interface SpecificationValue permits NumericValue, TextValue, BooleanValue { }

    @Value
    public static class NumericValue implements SpecificationValue {
        @NotNull
        BigDecimal amount;

        @AssertTrue(message = "Numeric operand exceeds precision/scale bounds")
        public boolean isPrecisionValid() {
            return decimalWithinBounds(amount);
        }
    }

    @Value
    public static class TextValue implements SpecificationValue {
        @NotBlank
        String text;

        @AssertTrue(message = "Text operand must have 1 to 128 normalized code points")
        public boolean isLengthValid() {
            if (text == null) {
                return true;
            }
            try {
                String normalized = new ProductSearchNormalizer().normalize(text);
                int length = normalized.codePointCount(0, normalized.length());
                return length >= 1 && length <= 128;
            } catch (InvalidCatalogRequestException exception) {
                return false;
            }
        }
    }

    @Value
    public static class BooleanValue implements SpecificationValue {
        @NotNull
        Boolean flag;
    }
}
