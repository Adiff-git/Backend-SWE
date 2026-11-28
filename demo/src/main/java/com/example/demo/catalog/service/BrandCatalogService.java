package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.response.BrandListItem;
import com.example.demo.catalog.repository.BrandRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandCatalogService {
    private final BrandRepository brands;

    @Transactional(readOnly = true)
    public List<BrandListItem> listBrands() {
        return brands.findAllWithProductCounts();
    }
}
