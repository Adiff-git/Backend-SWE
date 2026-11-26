package com.example.demo.catalog.dto.response;

import java.util.List;
import lombok.Value;

@Value
public class OptionDimension {
    String key;
    String label;
    List<OptionValue> values;

    public OptionDimension(String key, String label, List<OptionValue> values) {
        this.key = key;
        this.label = label;
        this.values = List.copyOf(values);
    }
}
