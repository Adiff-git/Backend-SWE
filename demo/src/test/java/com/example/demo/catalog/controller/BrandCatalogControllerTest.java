package com.example.demo.catalog.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.catalog.dto.response.BrandListItem;
import com.example.demo.catalog.service.BrandCatalogService;
import com.example.demo.common.security.CatalogSecurityConfiguration;
import com.example.demo.common.security.CatalogSecurityProblemHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BrandCatalogController.class)
@Import({CatalogSecurityConfiguration.class, CatalogSecurityProblemHandler.class,
        com.example.demo.common.exception.ApiErrorFactory.class})
class BrandCatalogControllerTest {
    @MockitoBean private com.example.demo.auth.security.CurrentStateJwtConverter converter;
    @MockitoBean private org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    @MockitoBean private BrandCatalogService service;
    @Autowired private MockMvc mvc;

    @Test
    void anonymousReadReturnsBrandIdentities() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listBrands()).thenReturn(List.of(new BrandListItem(id, "apple", "Apple", 3)));
        mvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].code").value("apple"))
                .andExpect(jsonPath("$[0].name").value("Apple"))
                .andExpect(jsonPath("$[0].productCount").value(3));
    }

    @Test
    void emptyCatalogReturnsEmptyArray() throws Exception {
        when(service.listBrands()).thenReturn(List.of());
        mvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
