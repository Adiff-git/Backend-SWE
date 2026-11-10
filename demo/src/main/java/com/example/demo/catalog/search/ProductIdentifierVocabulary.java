package com.example.demo.catalog.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

/** Approved fuzzy exclusions only;source anchors never establish canonical Product ownership. */
public final class ProductIdentifierVocabulary {
    private final Set<String> families = new HashSet<>();
    private final Map<String, List<String>> contexts = new HashMap<>();
    private final ProductSearchNormalizer normalizer = new ProductSearchNormalizer();

    public ProductIdentifierVocabulary() {
        try (var in = getClass().getResourceAsStream("/catalog/identifier-vocabulary-v1.json")) {
            if (in == null) { throw new IllegalStateException("Missing identifier vocabulary"); }
            var root = JsonMapper.builder().build().readTree(in);
            root.get("families").forEach(value -> families.add(value.asString()));
            root.get("contexts").properties().forEach(entry -> {
                List<String> spans = new ArrayList<>();
                entry.getValue().forEach(value -> spans.add(" " + value.asString() + " "));
                contexts.put(entry.getKey(), List.copyOf(spans));
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Invalid identifier vocabulary", exception);
        }
    }

    public boolean isProtected(ProductSearchNormalizer.Token token, String text) {
        if (token.isStructurallyProtected() || families.contains(token.getText())) { return true; }
        String context = " " + String.join(" ", normalizer.tokenize(text).stream()
                .map(ProductSearchNormalizer.Token::getText).toList()) + " ";
        return contexts.getOrDefault(token.getText(), List.of()).stream().anyMatch(context::contains);
    }
}
