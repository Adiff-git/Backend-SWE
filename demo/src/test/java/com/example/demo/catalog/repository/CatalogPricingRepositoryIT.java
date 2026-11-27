package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.fixture.catalog.CatalogSaleFixtures;
import java.math.BigDecimal;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CatalogPricingRepositoryIT {
    @Autowired private JdbcTemplate jdbc;

    @Test
    void effectivePrice_shouldRespectEnabledStateAndInclusiveExclusiveBounds() {
        for (CatalogSaleFixtures.SaleCase sale : CatalogSaleFixtures.saleCases()) {
            BigDecimal actual = jdbc.queryForObject(
                    "SELECT catalog_effective_price_v1(1000000::NUMERIC,?::NUMERIC,?,?::TIMESTAMPTZ,?::TIMESTAMPTZ,?::TIMESTAMPTZ)",
                    BigDecimal.class, sale.percentage(), sale.enabled(),
                    sale.start() == null ? null : Timestamp.from(sale.start()),
                    sale.end() == null ? null : Timestamp.from(sale.end()), Timestamp.from(sale.at()));
            assertThat(actual).as(sale.name()).isEqualByComparingTo(BigDecimal.valueOf(sale.effectiveAmount()));
        }
    }

    @Test
    void effectivePrice_shouldFloorUnitDiscountAndAllowZeroAndFullReduction() {
        assertThat(price("101", "12.50")).isEqualByComparingTo("89");
        assertThat(price("0", "20")).isEqualByComparingTo("0");
        assertThat(price("101", "100")).isEqualByComparingTo("0");
    }

    @Test
    void effectivePrice_shouldReturnNullForInvalidBase() {
        assertThat(price("-1", "20")).isNull();
        assertThat(price("1.5", "20")).isNull();
        assertThat(price("NaN", "20")).isNull();
        assertThat(price("Infinity", "20")).isNull();
    }

    @Test
    void effectivePrice_shouldRejectMissingEvaluationInstant() {
        assertThatThrownBy(() -> jdbc.queryForObject(
                "SELECT catalog_effective_price_v1(100::NUMERIC,20::NUMERIC,TRUE,NULL,NULL,NULL)",
                BigDecimal.class)).hasMessageContaining("pricing instant");
    }

    @Test
    void optionIdentity_shouldNormalizeUnicodeWithoutRemovingAccents() {
        assertThat(jdbc.queryForObject("SELECT catalog_option_identity_v1(?)", String.class, "\u00a0ĐỎ\u00a0"))
                .isEqualTo("đỏ");
        assertThat(jdbc.queryForObject("SELECT catalog_option_identity_v1(?) = catalog_option_identity_v1(?)",
                Boolean.class, "É", "E\u0301")).isTrue();
        assertThat(jdbc.queryForObject("SELECT catalog_option_identity_v1(?) = catalog_option_identity_v1(?)",
                Boolean.class, "É", "E")).isFalse();
    }

    @Test
    void optionIdentity_shouldUseRootCaseRulesAndEverySpecifiedEdgeWhitespace() {
        String[][] cases = {{"I","i"},{"\u0130","i\u0307"},{"\u0131","\u0131"},
                {"\u03a3","\u03c3"},{"\ud801\udc00","\ud801\udc28"}};
        for (String[] pair : cases) {
            assertThat(jdbc.queryForObject("SELECT catalog_option_identity_v1(?)", String.class, pair[0])).isEqualTo(pair[1]);
        }
        for (int code : new int[]{9,10,11,12,13,32,133,160,5760,8192,8193,8194,8195,8196,8197,
                8198,8199,8200,8201,8202,8232,8233,8239,8287,12288}) {
            String edge = Character.toString(code);
            assertThat(jdbc.queryForObject("SELECT catalog_option_identity_v1(?)", String.class, edge+"A"+edge)).isEqualTo("a");
        }
    }

    private BigDecimal price(String base, String percentage) {
        return jdbc.queryForObject(
                "SELECT catalog_effective_price_v1(?::NUMERIC,?::NUMERIC,TRUE,NULL,NULL,?::TIMESTAMPTZ)",
                BigDecimal.class, base, percentage, Timestamp.from(CatalogSaleFixtures.START));
    }
}
