package com.example.demo.catalog.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"spring.datasource.url=jdbc:tc:postgresql:17-alpine:///search_exact_only",
        "catalog.search.fuzzy.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchExactOnlyIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Test void disabledFuzzy_shouldExposeModeAndStillRequireInstalledCapability()throws Exception{
        mvc.perform(get("/api/products").param("q","samsnug")).andExpect(status().isOk())
                .andExpect(header().string("X-Catalog-Search-Mode","exact-only"));
        String definition=jdbc.queryForObject("SELECT pg_get_functiondef('catalog_search_protected_v1(text,text)'::regprocedure)",String.class);
        try {jdbc.execute("DROP FUNCTION catalog_search_protected_v1(text,text)");
            mvc.perform(get("/api/products").param("q","catalog-readiness-probe")).andExpect(status().isServiceUnavailable());
        } finally {jdbc.execute(definition);}
    }
}
