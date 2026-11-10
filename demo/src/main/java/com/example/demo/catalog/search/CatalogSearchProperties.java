package com.example.demo.catalog.search;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "catalog.search.fuzzy")
public class CatalogSearchProperties {
    private boolean enabled = true;
    @DecimalMin(value = "0", inclusive = false)
    @DecimalMax("1")
    private double threshold = 0.32; // Measured controlled-corpus operating point;see calibration evidence.
}
