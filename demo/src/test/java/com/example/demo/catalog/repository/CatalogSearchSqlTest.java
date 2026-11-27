package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.ProductSearchNormalizer;
import com.example.demo.catalog.search.SpecificationFilterRegistry;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SQL composition/binding only; PostgreSQL parsing, results and plans remain gated IT obligations. */
class CatalogSearchSqlTest {
    @Test void recheck_shouldReuseEligibilityFiltersAndRestrictOriginalProductVariantSetWithoutRepagination(){
        UUID product=UUID.randomUUID(),variant=UUID.randomUUID(),category=UUID.randomUUID(),brand=UUID.randomUUID();
        var criteria=ProductSearchCriteria.builder().categoryId(category).brandIds(List.of(brand))
                .minPrice(BigInteger.TEN).maxPrice(BigInteger.valueOf(1000)).page(9).size(20)
                .keyword(new ProductSearchNormalizer().normalizeQuery("phone")).build();
        var sql=new CatalogSearchSql(new SpecificationFilterRegistry());
        var current=sql.recheck(criteria,.32,true,List.of(product),List.of(variant));
        assertThat(current.sql()).contains(CatalogApplicabilitySql.registryCtes(),"v.enabled=true",
                "p.brand_id IN(:brands)","p.category_id IN(SELECT id FROM category_scope)",
                "v.effective_price>=:minPrice","v.effective_price<=:maxPrice","v.id IN(:recheckVariants)",
                "p.id IN(:recheckProducts)","i.active=true AND i.is_primary=true","FROM ranked_variants r");
        assertThat(current.sql()).doesNotContain("LIMIT :size","OFFSET :offset",product.toString(),variant.toString());
        assertThat(current.parameters()).containsEntry("recheckProducts",List.of(product)).containsEntry("recheckVariants",List.of(variant))
                .containsEntry("minPrice",BigInteger.TEN).containsEntry("maxPrice",BigInteger.valueOf(1000));
        assertThat(sql.compile(criteria,.32,true).sql()).contains("JOIN product_variants v", "v.enabled=true");
    }
}
