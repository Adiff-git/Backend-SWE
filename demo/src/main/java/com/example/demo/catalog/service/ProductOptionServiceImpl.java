package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.response.OptionDimension;
import com.example.demo.catalog.dto.response.OptionValue;
import com.example.demo.catalog.entity.ProductVariant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/** Orders identities already normalized by PostgreSQL; preserves original display labels. */
@Service
public class ProductOptionServiceImpl implements ProductOptionService {
    private static final Comparator<String> CODE_POINTS = (left, right) ->
            Arrays.compare(left.codePoints().toArray(), right.codePoints().toArray());

    @Override
    public List<OptionDimension> getDimensions(List<ProductVariant> variants) {
        Map<java.util.UUID,Map<String,Map<String,String>>> labels=new java.util.HashMap<>();
        variants.stream().filter(variant -> variant.getCanonicalOptionLabels()!=null)
                .forEach(variant -> labels.put(variant.getId(),variant.getCanonicalOptionLabels()));
        return getPreparedDimensions(labels);
    }

    @Override public List<OptionDimension> getPreparedDimensions(Map<java.util.UUID,Map<String,Map<String,String>>> prepared) {
        Map<String, String> labels = new TreeMap<>(CODE_POINTS);
        Map<String, Map<String, String>> values = new TreeMap<>(CODE_POINTS);
        prepared.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString())).forEach(variant ->
                    variant.getValue().forEach((key, entry) -> {
                        labels.putIfAbsent(key, entry.get("label"));
                        values.computeIfAbsent(key, ignored -> new TreeMap<>(CODE_POINTS))
                                .putIfAbsent(entry.get("value"), entry.get("valueLabel"));
                    }));
        List<OptionDimension> dimensions = new ArrayList<>();
        labels.forEach((key, label) -> dimensions.add(new OptionDimension(key, label,
                values.get(key).entrySet().stream().map(entry -> new OptionValue(entry.getKey(), entry.getValue())).toList())));
        return List.copyOf(dimensions);
    }
}
