package com.example.demo.inventory.controller.admin;

import com.example.demo.inventory.dto.response.AdminInventoryOperation;
import com.example.demo.inventory.dto.response.AdminInventoryPage;
import com.example.demo.inventory.dto.response.AdminInventoryVariant;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.inventory.service.AdminInventoryReadService;
import com.example.demo.inventory.validator.AdminInventoryValidator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inventory/variants")
public class AdminInventoryReadController {
    private final AdminInventoryReadService reads;
    private final AdminInventoryValidator validator;

    @GetMapping
    public AdminInventoryPage<AdminInventoryVariant> list(@RequestParam MultiValueMap<String, String> params) {
        if (!Set.of("q", "status", "page", "size").containsAll(params.keySet())) throw invalid();
        Set<ProductStatus> statuses = EnumSet.noneOf(ProductStatus.class);
        for (String value : params.getOrDefault("status", List.of())) {
            try { statuses.add(ProductStatus.valueOf(value)); }
            catch (IllegalArgumentException ignored) { throw invalid(); }
        }
        return reads.list(one(params, "q", null), statuses, number(params, "page", "0", 0, Integer.MAX_VALUE),
                number(params, "size", "20", 1, 100));
    }

    @GetMapping("/{variantId}")
    public AdminInventoryVariant detail(@PathVariable String variantId) {
        return reads.detail(validator.identifier(variantId));
    }

    @GetMapping("/{variantId}/history")
    public AdminInventoryPage<AdminInventoryOperation> history(@PathVariable String variantId, @RequestParam MultiValueMap<String, String> params) {
        if (!Set.of("page", "size").containsAll(params.keySet())) throw invalid();
        return reads.history(validator.identifier(variantId), number(params, "page", "0", 0, Integer.MAX_VALUE),
                number(params, "size", "20", 1, 100));
    }

    private int number(MultiValueMap<String, String> params, String key, String fallback, int min, int max) {
        try {
            String value = one(params, key, fallback);
            if (!value.matches("0|[1-9][0-9]{0,9}")) throw invalid();
            int number = Integer.parseInt(value);
            if (number < min || number > max) throw invalid();
            return number;
        } catch (NumberFormatException ignored) { throw invalid(); }
    }

    private String one(MultiValueMap<String, String> params, String key, String fallback) {
        List<String> values = params.get(key);
        if (values == null) return fallback;
        if (values.size() != 1) throw invalid();
        return values.getFirst();
    }

    private RuntimeException invalid() { return AdminInventoryValidator.invalidRequest(); }
}
