package com.example.demo.catalog.search;

import com.example.demo.catalog.exception.SearchCapabilityUnavailableException;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** No extension install/repair here;startup and each keyword query fail explicitly on capability loss. */
@Component
@DependsOnDatabaseInitialization
public class CatalogSearchCapabilityState {
    public static final String SQL = """
        SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname='pg_trgm' AND extversion='1.6' AND extnamespace='public'::regnamespace)
          AND to_regprocedure('public.similarity(text,text)') IS NOT NULL
          AND to_regoperator('public.<<%(text,text)') IS NOT NULL
          AND to_regprocedure('public.catalog_search_tokens_v1(text)') IS NOT NULL
          AND to_regprocedure('public.catalog_search_protected_v1(text,text)') IS NOT NULL
          AND to_regprocedure('public.catalog_search_phrase_v1(text,text)') IS NOT NULL
          AND to_regprocedure('public.catalog_search_typo_v1(text,text)') IS NOT NULL
          AND to_regprocedure('public.catalog_search_normalize_v2(text)') IS NOT NULL
          AND to_regprocedure('public.catalog_search_safe_normalize_v2(text)') IS NOT NULL
          AND to_regprocedure('public.catalog_search_description_tokens_v1(text)') IS NOT NULL
          AND EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid='public.products'::regclass AND attname='catalog_description_tokens_v1' AND attgenerated='s' AND NOT attisdropped)
        """;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private volatile Boolean last;
    public CatalogSearchCapabilityState(JdbcTemplate jdbc,ApplicationEventPublisher events){this.jdbc=jdbc;this.events=events;}
    @PostConstruct void initialize(){
        if (!Boolean.TRUE.equals(jdbc.queryForObject(SQL,Boolean.class))) { throw new IllegalStateException("Required catalog search capability unavailable"); }
    }
    public synchronized void observe(boolean healthy){
        if (!Boolean.valueOf(healthy).equals(last)) {
            last=healthy;
            AvailabilityChangeEvent.publish(events,this,healthy?ReadinessState.ACCEPTING_TRAFFIC:ReadinessState.REFUSING_TRAFFIC);
        }
        if(!healthy){throw new SearchCapabilityUnavailableException();}
    }
}
