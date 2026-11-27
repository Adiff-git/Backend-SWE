package com.example.demo.catalog.repository;

import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.catalog.exception.SearchCapabilityUnavailableException;
import com.example.demo.catalog.search.CatalogSearchCapabilityState;
import com.example.demo.catalog.search.ProductCandidatePage;
import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.ProductSearchSort;
import com.example.demo.catalog.search.SpecificationFilterRegistry;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

/** ID-first page/count on one statement snapshot;Catalog service retains batch loading and signing. */
public class ProductSearchRepositoryImpl implements ProductSearchRepository {
    private final EntityManager entityManager;
    private final ObjectProvider<CatalogSearchCapabilityState> availability;
    private final SpecificationFilterRegistry registry = new SpecificationFilterRegistry();
    @Value("${catalog.search.fuzzy.enabled:true}") private boolean fuzzyEnabled = true;
    @Value("${catalog.search.fuzzy.threshold:0.32}") private double threshold = 0.32;
    public ProductSearchRepositoryImpl(EntityManager entityManager,ObjectProvider<CatalogSearchCapabilityState> availability){
        this.entityManager=entityManager;this.availability=availability;
    }
    @Override
    @Transactional(readOnly=true)
    public java.util.List<CurrentCandidate> recheckPublishedCandidates(ProductSearchCriteria criteria,
            java.util.List<ProductCandidatePage.Candidate> candidates){
        var eligible=candidates.stream().filter(c -> c.getRepresentativeVariantId()!=null).toList();
        if(eligible.isEmpty())return java.util.List.of();
        var statement=new CatalogSearchSql(registry).recheck(criteria,threshold,fuzzyEnabled,
                eligible.stream().map(ProductCandidatePage.Candidate::getProductId).distinct().toList(),
                eligible.stream().map(ProductCandidatePage.Candidate::getRepresentativeVariantId).distinct().toList());
        var query=entityManager.createNativeQuery(statement.sql());statement.parameters().forEach(query::setParameter);
        var expected=eligible.stream().collect(java.util.stream.Collectors.toMap(ProductCandidatePage.Candidate::getProductId,
                ProductCandidatePage.Candidate::getRepresentativeVariantId));
        var result=new ArrayList<CurrentCandidate>();
        for(Object value:query.getResultList()){
            Object[] row=(Object[])value;
            if(java.util.Objects.equals(expected.get(row[0]),row[1]))result.add(new CurrentCandidate(
                    (java.util.UUID)row[0],(java.util.UUID)row[1],((BigDecimal)row[2]).toBigIntegerExact(),
                    (String)row[3],(java.util.UUID)row[4],(String)row[5],(String)row[6],
                    new ProductCandidatePage.Candidate((java.util.UUID)row[0], (java.util.UUID)row[1], integer(row[2]),
                            integer(row[7]), integer(row[8]), (BigDecimal)row[9], row[9]==null?null:instant(row[10]),
                            row[9]==null?null:instant(row[11]),criteria.getPricingInstant(), (java.util.UUID)row[12],
                            integer(row[13]), integer(row[14]), integer(row[15]))));
        }
        return java.util.List.copyOf(result);
    }
    @Override
    @Transactional(readOnly=true)
    public ProductCandidatePage findPublishedCandidates(ProductSearchCriteria criteria){
        if(criteria==null || criteria.getPage()<0 || criteria.getSize()<1 || criteria.getSize()>100
                || criteria.getBrandIds()!=null && criteria.getBrandIds().isEmpty()){
            throw new InvalidCatalogRequestException("Invalid candidate pagination or brands");
        }
        registry.validate(criteria.getSpecificationGroups(),criteria.getCategoryId());
        if(!(threshold>0 && threshold<=1)){throw new IllegalStateException("Invalid catalog fuzzy threshold");}
        boolean keyword=criteria.getKeyword()!=null&&!criteria.getKeyword().getTokens().isEmpty();
        if(criteria.getSort()==ProductSearchSort.RELEVANCE&&!keyword){throw new InvalidCatalogRequestException("Relevance requires q");}
        try{
            if(keyword){
                boolean healthy=Boolean.TRUE.equals(entityManager.createNativeQuery(CatalogSearchCapabilityState.SQL).getSingleResult());
                var state=availability.getIfAvailable();if(state!=null){state.observe(healthy);}else if(!healthy){throw new SearchCapabilityUnavailableException();}
                if(fuzzyEnabled){entityManager.createNativeQuery("SELECT set_config('pg_trgm.strict_word_similarity_threshold',:threshold,true)")
                        .setParameter("threshold",Double.toString(threshold)).getSingleResult();}
            }
            var statement=new CatalogSearchSql(registry).compile(criteria,threshold,fuzzyEnabled);
            var query=entityManager.createNativeQuery(statement.sql());statement.parameters().forEach(query::setParameter);
            var rows=query.getResultList();
            if(rows.isEmpty()){throw new IllegalStateException("Candidate count projection missing");}
            Object[] first=(Object[])rows.getFirst();
            if(Boolean.TRUE.equals(first[4])){throw new InvalidCatalogRequestException("Unsupported specification filters for selected category");}
            var items=new ArrayList<ProductCandidatePage.Candidate>();
            for(Object result:rows){var row=(Object[])result;if(row[0]!=null){items.add(new ProductCandidatePage.Candidate(
                    (java.util.UUID)row[0],(java.util.UUID)row[1],integer(row[2]),integer(row[5]),integer(row[6]),(BigDecimal)row[7],
                    row[7]==null?null:instant(row[8]),row[7]==null?null:instant(row[9]),criteria.getPricingInstant(),
                    (java.util.UUID)row[10],integer(row[11]),integer(row[12]),integer(row[13])));}}
            return new ProductCandidatePage(items,((Number)first[3]).longValue());
        }catch(jakarta.persistence.PersistenceException exception){
            if(keyword){for(Throwable cause=exception;cause!=null;cause=cause.getCause()){
                if(cause instanceof java.sql.SQLException sql && java.util.Set.of("42883","42704","42703").contains(sql.getSQLState())
                        && java.util.List.of("catalog_search_", "catalog_description_tokens_v1", "similarity(", "<<%", "pg_trgm").stream().anyMatch(fragment -> sql.getMessage().contains(fragment))){
                    var state=availability.getIfAvailable();if(state!=null){state.observe(false);}throw new SearchCapabilityUnavailableException();
                }
            }}throw exception;
        }
    }
    private static java.math.BigInteger integer(Object value) {
        return value == null ? null : ((BigDecimal)value).toBigIntegerExact();
    }
    private static java.time.Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.Instant at) return at;
        if (value instanceof java.time.OffsetDateTime at) return at.toInstant();
        if (value instanceof java.sql.Timestamp at) return at.toInstant();
        throw new IllegalStateException("Unsupported pricing timestamp projection");
    }

}
