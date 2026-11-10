package com.example.demo.catalog.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ProductSearchNormalizerTest {
    private final ProductSearchNormalizer normalizer = new ProductSearchNormalizer();

    @Test
    void matchesDocumentedD2GoldenCases() throws Exception {
        try (InputStream resource = getClass().getResourceAsStream("/catalog/search/normalization-cases.json")) {
            assertThat(resource).isNotNull();
            JsonNode cases = new ObjectMapper().readTree(resource);
            assertThat(cases.size()).isEqualTo(16);
            for (JsonNode example : cases) {
                List<ProductSearchNormalizer.Token> tokens = normalizer.tokenize(example.get("input").asString());
                assertThat(tokens).hasSize(example.get("tokens").size());
                for (int index = 0; index < tokens.size(); index++) {
                    assertThat(tokens.get(index).getText()).isEqualTo(example.get("tokens").get(index).asString());
                    assertThat(tokens.get(index).isStructurallyProtected())
                            .isEqualTo(example.get("protected").get(index).asBoolean());
                }
            }
        }
    }

    @Test
    void nfcNfdAndVietnameseMarksHaveSameNormalization() {
        String text = "ĐẮNG điện thoại Ờ Ự Ỷ";
        assertThat(normalizer.normalize(text)).isEqualTo("dang dien thoai o u y");
        assertThat(normalizer.normalize(Normalizer.normalize(text, Normalizer.Form.NFD)))
                .isEqualTo(normalizer.normalize(text));
    }

    @Test
    void consumesExactlyTheDocumentedUnicodeWhitespace() {
        int[] whitespace = {9, 10, 11, 12, 13, 32, 0x85, 0xa0, 0x1680, 0x2000, 0x2001,
                0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200a,
                0x2028, 0x2029, 0x202f, 0x205f, 0x3000};
        for (int cp : whitespace) {
            String separator = new String(Character.toChars(cp));
            assertThat(normalizer.normalize(separator + "Điện" + separator + "THOẠI" + separator))
                    .isEqualTo("dien thoai");
        }
    }

    @Test
    void lowercaseDoesNotDependOnHostLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThat(normalizer.normalize("I7 ĐIỆN")).isEqualTo("i7 dien");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void deduplicatesMembershipButKeepsPhraseAndSequence() {
        ProductSearchNormalizer.Query query = normalizer.normalizeQuery("  ĐIỆN điện   THOẠI ");
        assertThat(query.getOriginalNormalized()).isEqualTo("dien dien thoai");
        assertThat(query.getSequence()).extracting(ProductSearchNormalizer.Token::getText)
                .containsExactly("dien", "dien", "thoai");
        assertThat(query.getTokens()).extracting(ProductSearchNormalizer.Token::getText)
                .containsExactly("dien", "thoai");
        assertThatThrownBy(() -> query.getTokens().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void limitsRawTokensBeforeDeduplication() {
        assertThat(normalizer.normalizeQuery("sony ".repeat(16)).getTokens()).hasSize(1);
        assertThatThrownBy(() -> normalizer.normalizeQuery("sony ".repeat(17)))
                .isInstanceOf(InvalidCatalogRequestException.class);
    }

    @Test
    void queryLengthCountsUnicodeCodePointsBeforeNormalization() {
        assertThat(normalizer.normalizeQuery("𐐀".repeat(256)).getTokens()).hasSize(1);
        assertThatThrownBy(() -> normalizer.normalizeQuery("𐐀".repeat(257)))
                .isInstanceOf(InvalidCatalogRequestException.class);
        assertThatThrownBy(() -> normalizer.normalizeQuery(" ".repeat(257)))
                .isInstanceOf(InvalidCatalogRequestException.class);
    }

    @Test
    void rejectsControlsAndNonblankZeroTokensButBlankIsOmitted() {
        for (String invalid : List.of("***", "---", "\u0000sony", "sony\u007f")) {
            assertThatThrownBy(() -> normalizer.normalizeQuery(invalid))
                    .isInstanceOf(InvalidCatalogRequestException.class);
        }
        assertThat(normalizer.normalizeQuery(null).getTokens()).isEmpty();
        assertThat(normalizer.normalizeQuery("\u0085 \t").getTokens()).isEmpty();
        assertThat(normalizer.normalizeQuery("null").getTokens()).hasSize(1);
    }

    @Test
    void doesNotApplyQueryLimitsToCandidateFieldsOrInventVocabulary() {
        assertThat(normalizer.tokenize("model ".repeat(50))).hasSize(50);
        assertThat(normalizer.tokenize("thinkpad").getFirst().isStructurallyProtected()).isFalse();
        // Structural status says nothing about approved alphabetic models/fuzzy eligibility.
        assertThat(normalizer.normalize("16.0 16GB")).isEqualTo("16.0 16gb");
    }
}
