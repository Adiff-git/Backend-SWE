package com.example.demo.catalog.search;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
class ProductIdentifierVocabularyTest {
    private final ProductSearchNormalizer normalizer=new ProductSearchNormalizer();
    private final ProductIdentifierVocabulary vocabulary=new ProductIdentifierVocabulary();
    @Test void families_shouldBeProtectedIncludingChromebook_withoutModelPromotion() throws Exception{
        var root=JsonMapper.builder().build().readTree(getClass().getResourceAsStream("/catalog/identifier-vocabulary-v1.json"));
        assertThat(root.get("families").size()).isEqualTo(19);assertThat(root.get("modelAnchors").isEmpty()).isTrue();
        for(var family:root.get("families")){var q=normalizer.normalizeQuery(family.asString());assertThat(vocabulary.isProtected(q.getTokens().getFirst(),family.asString())).isTrue();}
    }
    @Test void ambiguousTerms_shouldRequireExactContext_andNeverRewritePlus(){
        for(String input:List.of("Microsoft Surface Pro","Google Pixel","HP Pavilion","Intel Core","Galaxy A03 Core","LG K22+ Plus","Galaxy S20+ Plus")){
            var ts=normalizer.tokenize(input);assertThat(ts).anyMatch(t->vocabulary.isProtected(t,input));
        }
        var generic=normalizer.tokenize("surface").getFirst();assertThat(vocabulary.isProtected(generic,"Use on Any Surface")).isFalse();
        var plus=normalizer.tokenize("plus").getFirst();assertThat(vocabulary.isProtected(plus,"LG K22 Plus")).isFalse();assertThat(vocabulary.isProtected(plus,"LG K22+ Plus")).isTrue();
    }
    @Test void originalDraftCases_shouldRetainAllSixteenPrimitiveExpectations() throws Exception{
        var cases=JsonMapper.builder().build().readTree(getClass().getResourceAsStream("/catalog/search/identifier-cases.json")).get("cases");
        assertThat(cases.size()).isEqualTo(16);
        for(var c:cases){var ts=normalizer.tokenize(c.get("input").asString());List<String> expected=new ArrayList<>();c.get("expectedTokens").forEach(v->expected.add(v.asString()));assertThat(ts).extracting(ProductSearchNormalizer.Token::getText).containsExactlyElementsOf(expected);}
    }
    @Test void everyApprovedContext_shouldProtectItsModifier_butNotGenericUsage()throws Exception{
        var contexts=JsonMapper.builder().build().readTree(getClass().getResourceAsStream("/catalog/identifier-vocabulary-v1.json")).get("contexts");
        contexts.properties().forEach(entry->{
            var token=normalizer.tokenize(entry.getKey()).getFirst();
            assertThat(vocabulary.isProtected(token,"ordinary "+entry.getKey()+" word")).isFalse();
            entry.getValue().forEach(span->assertThat(vocabulary.isProtected(token,span.asString())).as(span.asString()).isTrue());
        });
    }
}
