package com.example.demo.catalog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.catalog.dto.response.ProductFilterOptions;
import com.example.demo.catalog.repository.ProductFilterOptionsRepository;
import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.common.security.CatalogSecurityConfiguration;
import com.example.demo.common.security.CatalogSecurityProblemHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductFilterOptionsController.class)
@Import({CatalogSecurityConfiguration.class, CatalogSecurityProblemHandler.class,
        com.example.demo.common.exception.ApiExceptionHandler.class,
        com.example.demo.common.exception.ApiErrorFactory.class})
class ProductFilterOptionsControllerTest {
    @MockitoBean com.example.demo.auth.security.CurrentStateJwtConverter converter;
    @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    @MockitoBean ProductFilterOptionsRepository repository;
    @Autowired MockMvc mvc;

    @Test void publicOptionsExposeTypedValuesCountsAndScope() throws Exception {
        var category = UUID.randomUUID();
        var brand = UUID.randomUUID();
        when(repository.findOptions(any())).thenReturn(List.of(new ProductFilterOptions("physical_ram", "quantity", "GB",
                List.of(new ProductFilterOptions.Option(16, 3)))));
        mvc.perform(get("/api/product-filter-options").param("categoryId", category.toString()).param("brandId", brand.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].key").value("physical_ram"))
                .andExpect(jsonPath("$[0].unit").value("GB"))
                .andExpect(jsonPath("$[0].options[0].value").value(16))
                .andExpect(jsonPath("$[0].options[0].productCount").value(3));
        var captured = ArgumentCaptor.forClass(ProductSearchCriteria.class);
        verify(repository).findOptions(captured.capture());
        assertThat(captured.getValue().getCategoryId()).isEqualTo(category);
        assertThat(captured.getValue().getBrandIds()).containsExactly(brand);
    }

    @Test void invalidScopeIsRejectedBeforeQuery() throws Exception {
        mvc.perform(get("/api/product-filter-options").param("categoryId", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/product-filter-options").param("categoryId", UUID.randomUUID().toString()).param("ram", "16"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test void publicOptionsNeedNoCategoryOrBrand() throws Exception {
        when(repository.findOptions(any())).thenReturn(List.of());
        mvc.perform(get("/api/product-filter-options")).andExpect(status().isOk());
        var captured = ArgumentCaptor.forClass(ProductSearchCriteria.class);
        verify(repository).findOptions(captured.capture());
        assertThat(captured.getValue().getCategoryId()).isNull();
        assertThat(captured.getValue().getBrandIds()).isNull();
    }
}
