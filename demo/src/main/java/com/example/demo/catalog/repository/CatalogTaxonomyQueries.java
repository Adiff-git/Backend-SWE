package com.example.demo.catalog.repository;

import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.Query;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.Ordering;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Brand;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Category;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.CategoryPage;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Page;
import com.example.demo.catalog.exception.AdminCatalogException;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Catalog-owned SQL projections. No Java name equality/tree resolver or business mutation here. */
@Repository
@RequiredArgsConstructor
public class CatalogTaxonomyQueries {
    private final EntityManager entities;
    public record BindingScope(long matches,UUID root,Set<UUID> categories){public BindingScope{categories=Set.copyOf(categories);}}
    public String normalizeName(String value){return (String)entities.createNativeQuery("SELECT catalog_name_trim(:value)").setParameter("value",value).getSingleResult();}
    public boolean categoryNameExists(String name,UUID parent,UUID exclude){
        return (Boolean)entities.createNativeQuery("SELECT EXISTS(SELECT 1 FROM categories WHERE catalog_name_key(name)=catalog_name_key(:name) AND parent_category_id IS NOT DISTINCT FROM CAST(:parent AS uuid) AND (CAST(:exclude AS uuid) IS NULL OR id<>CAST(:exclude AS uuid)))")
                .setParameter("name",name).setParameter("parent",parent).setParameter("exclude",exclude).getSingleResult();
    }
    public boolean brandNameExists(String name,UUID exclude){return (Boolean)entities.createNativeQuery("SELECT EXISTS(SELECT 1 FROM brands WHERE catalog_name_key(name)=catalog_name_key(:name) AND (CAST(:exclude AS uuid) IS NULL OR id<>CAST(:exclude AS uuid)))").setParameter("name",name).setParameter("exclude",exclude).getSingleResult();}
    public boolean categoryReferenced(UUID id){return (Boolean)entities.createNativeQuery("SELECT EXISTS(SELECT 1 FROM products WHERE category_id=:id) OR EXISTS(SELECT 1 FROM categories WHERE parent_category_id=:id)").setParameter("id",id).getSingleResult();}
    public boolean brandReferenced(UUID id){return (Boolean)entities.createNativeQuery("SELECT EXISTS(SELECT 1 FROM products WHERE brand_id=:id)").setParameter("id",id).getSingleResult();}
    public Set<UUID> descendants(UUID id){
        List<?> rows=entities.createNativeQuery("WITH RECURSIVE d(id) AS (SELECT id FROM categories WHERE id=:id UNION SELECT c.id FROM categories c JOIN d ON c.parent_category_id=d.id) SELECT id FROM d ORDER BY id").setParameter("id",id).getResultList();
        Set<UUID> result=new HashSet<>();rows.forEach(row->result.add((UUID)row));return Set.copyOf(result);
    }
    public List<UUID> publishedInCategories(Set<UUID> ids){
        if(ids.isEmpty())return List.of();List<?> rows=entities.createNativeQuery("SELECT id FROM products WHERE status='PUBLISHED' AND category_id IN (:ids) ORDER BY id").setParameter("ids",ids).getResultList();return rows.stream().map(UUID.class::cast).toList();
    }
    public Map<String,BindingScope> bindings(){
        String sql="WITH RECURSIVE "+CatalogApplicabilitySql.registryCtes()+"""
            SELECT f.family,(SELECT COUNT(*) FROM categories c WHERE catalog_search_safe_normalize_v2(c.name)=f.family),r.id,rc.id
            FROM (VALUES ('laptop'),('tablet')) f(family) LEFT JOIN registry_roots r ON r.family=f.family
            LEFT JOIN registry_categories rc ON rc.family=f.family ORDER BY f.family,rc.id
            """;
        List<?> rows=entities.createNativeQuery(sql).getResultList();Map<String,BindingScope> result=new LinkedHashMap<>();
        for(Object raw:rows){Object[] r=(Object[])raw;String family=(String)r[0];Set<UUID> ids=new HashSet<>(result.containsKey(family)?result.get(family).categories():Set.of());if(r[3]!=null)ids.add((UUID)r[3]);result.put(family,new BindingScope(((Number)r[1]).longValue(),(UUID)r[2],ids));}
        return Map.copyOf(result);
    }
    @Transactional(readOnly=true) public CategoryPage categories(Query query){
        List<?> rows=page(query,true);List<Category> result=new ArrayList<>();
        for(Object raw:rows){Object[] r=(Object[])raw;if(r[0]!=null)result.add(new Category((UUID)r[0],(String)r[1],(UUID)r[2],r[3].toString()));}
        Object[] first=(Object[])rows.getFirst();long count=((Number)first[4]).longValue();return new CategoryPage(List.copyOf(result),query.getPage().getPageNumber(),query.getPage().getPageSize(),count,pages(count,query),first[5].toString());
    }
    @Transactional(readOnly=true) public Page<Brand> brands(Query query){
        List<?> rows=page(query,false);List<Brand> result=new ArrayList<>();
        for(Object raw:rows){Object[] r=(Object[])raw;if(r[0]!=null)result.add(new Brand((UUID)r[0],(String)r[1],(String)r[2],r[3].toString()));}
        long count=((Number)((Object[])rows.getFirst())[4]).longValue();return new Page<>(List.copyOf(result),query.getPage().getPageNumber(),query.getPage().getPageSize(),count,pages(count,query));
    }
    private List<?> page(Query criteria,boolean category){
        if(criteria==null || criteria.getPage()==null || criteria.getPage().isUnpaged() || criteria.getPage().getPageSize()>100 || criteria.getOrdering()==null
                || category && Set.of(Ordering.CODE_ASC,Ordering.CODE_DESC).contains(criteria.getOrdering()) || !category && criteria.isParentSupplied())throw new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Invalid taxonomy query");
        Map<String,Object> parameters=new HashMap<>();String where=" WHERE TRUE";
        if(criteria.getQ()!=null){where+=" AND (LOWER(name) LIKE LOWER(:q) ESCAPE '!'"+(category?"":" OR LOWER(code) LIKE LOWER(:q) ESCAPE '!'")+")";parameters.put("q","%"+criteria.getQ().replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
        if(category && criteria.isParentSupplied()){if(criteria.getParent()==null)where+=" AND parent_category_id IS NULL";else{where+=" AND parent_category_id=:parent";parameters.put("parent",criteria.getParent());}}
        String order=criteria.getOrdering().sql();
        String sql="WITH selected AS (SELECT * FROM "+(category?"categories":"brands")+where+"), total AS (SELECT COUNT(*) n FROM selected), paged AS (SELECT * FROM selected ORDER BY "+order+" LIMIT :limit OFFSET :offset) SELECT p.id,"+(category?"p.name,p.parent_category_id":"p.code,p.name")+",p.version,total.n"+(category?",t.version":"")+" FROM total "+(category?"CROSS JOIN catalog_taxonomy_state t ":"")+"LEFT JOIN paged p ON TRUE "+(category?"WHERE t.id=1 ":"")+"ORDER BY p."+order.replace(", id",", p.id");
        var statement=entities.createNativeQuery(sql);parameters.forEach(statement::setParameter);statement.setParameter("limit",criteria.getPage().getPageSize()).setParameter("offset",criteria.getPage().getOffset());
        List<?> rows=statement.getResultList();if(rows.isEmpty())throw new AdminCatalogException(503,"CATALOG_MAINTENANCE_REQUIRED","Catalog revision service is unavailable");return rows;
    }
    private static int pages(long count,Query query){int size=query.getPage().getPageSize();return (int)Math.min(Integer.MAX_VALUE,count/size+(count%size==0?0:1));}
}
