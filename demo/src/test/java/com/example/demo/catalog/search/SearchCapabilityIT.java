package com.example.demo.catalog.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Fault injection owns a unique disposable database;never shares backend_test. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:tc:postgresql:17-alpine:///search_capability",
        "spring.datasource.hikari.maximum-pool-size=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchCapabilityIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired MockMvc mvc;
    @Autowired ApplicationAvailability availability;
    @Autowired CatalogSearchCapabilityState state;
    @Test void missingFunction_shouldRefuseKeywordAndReadiness_withoutBreakingLegacy_thenRecover()throws Exception{
        String definition=jdbc.queryForObject("SELECT pg_get_functiondef('catalog_search_protected_v1(text,text)'::regprocedure)",String.class);
        try {
            jdbc.execute("DROP FUNCTION catalog_search_protected_v1(text,text)");
            assertThatThrownBy(state::initialize).isInstanceOf(IllegalStateException.class);
            mvc.perform(get("/api/products").param("q","catalog-readiness-probe")).andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(header().doesNotExist("X-Catalog-Search-Mode"));
            assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);
            mvc.perform(get("/api/products")).andExpect(status().isOk());
        } finally {jdbc.execute(definition);}
        mvc.perform(get("/api/products").param("q","catalog-readiness-probe")).andExpect(status().isOk())
                .andExpect(header().string("X-Catalog-Search-Mode","fuzzy-enabled"));
        assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);
    }
    @Test void thresholdSetting_shouldBeTransactionLocal_onReusedSinglePoolConnection()throws Exception{
        try (var reserved=dataSource.getConnection()) { // one remaining connection is reused by every operation
        jdbc.queryForObject("SELECT similarity('warmup','warmup')",Double.class);
        String before=jdbc.queryForObject("SHOW pg_trgm.strict_word_similarity_threshold",String.class);
        mvc.perform(get("/api/products").param("q","samsnug")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SHOW pg_trgm.strict_word_similarity_threshold",String.class)).isEqualTo(before);
        }
    }
}
