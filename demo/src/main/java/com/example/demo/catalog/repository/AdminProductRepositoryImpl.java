package com.example.demo.catalog.repository;

import com.example.demo.catalog.dto.request.AdminProductRequests.Query;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.*;
import com.example.demo.catalog.exception.AdminCatalogException;
import jakarta.persistence.EntityManager;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class AdminProductRepositoryImpl implements AdminProductRepository {
    private final EntityManager entities;
    record Statement(String sql, Map<String,Object> parameters) { }

    static Statement statement(Query criteria) {
        if(criteria==null || criteria.getPage()==null || criteria.getPage().isUnpaged() || criteria.getPage().getPageSize()>100
                || criteria.getStatuses()==null || criteria.getOrdering()==null || criteria.isDescendants() && criteria.getCategoryId()==null)
            throw new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Invalid product query");
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        Map<String,Object> parameters = new LinkedHashMap<>();
        if(!criteria.getStatuses().isEmpty()) { where.append(" AND p.status IN (:states)"); parameters.put("states",criteria.getStatuses().stream().map(Enum::name).toList()); }
        if(criteria.getCategoryId()!=null) { where.append(" AND p.category_id IN (SELECT id FROM scope)"); parameters.put("category",criteria.getCategoryId()); }
        if(criteria.getBrandId()!=null) { where.append(" AND p.brand_id=:brand"); parameters.put("brand",criteria.getBrandId()); }
        if(criteria.getQ()!=null) {
            if(criteria.getQ().isBlank()) throw new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Invalid product query");
            where.append(" AND (LOWER(p.name) LIKE LOWER(:keyword) ESCAPE '!' OR EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=p.id AND LOWER(v.sku) LIKE LOWER(:keyword) ESCAPE '!'))");
            parameters.put("keyword","%"+criteria.getQ().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        }
        String scope=criteria.getCategoryId()==null?"scope AS (SELECT id FROM categories WHERE FALSE)":
                "scope AS (SELECT id FROM categories WHERE id=:category"+(criteria.isDescendants()?" UNION SELECT c.id FROM categories c JOIN scope s ON c.parent_category_id=s.id":"")+")";
        parameters.put("limit",criteria.getPage().getPageSize()); parameters.put("offset",criteria.getPage().getOffset());
        String sql="WITH RECURSIVE "+scope+", selected AS (SELECT p.* FROM products p"+where+"), total AS (SELECT COUNT(*) AS n FROM selected), paged AS (SELECT * FROM selected ORDER BY "+criteria.getOrdering().sql()+" LIMIT :limit OFFSET :offset) "+"""
            SELECT p.id,p.name,p.status,p.version,c.id,c.name,c.version,b.id,b.code,b.name,b.version,
                   (SELECT COUNT(*) FROM product_variants v WHERE v.product_id=p.id),
                   (SELECT COUNT(*) FROM product_variants v WHERE v.product_id=p.id AND v.enabled),total.n,t.version
            FROM total CROSS JOIN catalog_taxonomy_state t LEFT JOIN paged p ON TRUE
            LEFT JOIN categories c ON c.id=p.category_id LEFT JOIN brands b ON b.id=p.brand_id
            WHERE t.id=1
            """+" ORDER BY p."+criteria.getOrdering().sql().replace(", id",", p.id");
        return new Statement(sql,Map.copyOf(parameters));
    }

    @Override @Transactional(readOnly=true)
    public ProductPage findAdminPage(Query criteria) {
        Statement statement=statement(criteria);
        var query=entities.createNativeQuery(statement.sql()); statement.parameters().forEach(query::setParameter);
        List<?> rows=query.getResultList();
        if(rows.isEmpty()) throw new AdminCatalogException(503,"CATALOG_MAINTENANCE_REQUIRED","Catalog revision service is unavailable");
        List<ProductRow> result=new ArrayList<>();
        for(Object row:rows) {
            Object[] r=(Object[])row;
            if(r[0]==null) continue;
            var category=new CategoryReference((UUID)r[4],(String)r[5],r[6].toString());
            Brand brand=r[7]==null?null:new Brand((UUID)r[7],(String)r[8],(String)r[9],r[10].toString());
            result.add(new ProductRow((UUID)r[0],(String)r[1],(String)r[2],r[3].toString(),category,brand,((Number)r[11]).longValue(),((Number)r[12]).longValue()));
        }
        long total=((Number)((Object[])rows.getFirst())[13]).longValue();
        int size=criteria.getPage().getPageSize();
        return new ProductPage(List.copyOf(result),criteria.getPage().getPageNumber(),size,total,
                (int)Math.min(Integer.MAX_VALUE,total/size+(total%size==0?0:1)),((Object[])rows.getFirst())[14].toString());
    }
}
