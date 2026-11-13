package com.example.demo.behavior;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BehaviorServiceTest {
    final BehaviorStore store = mock(BehaviorStore.class);
    final com.example.demo.catalog.service.ProductCatalogService catalog = mock(com.example.demo.catalog.service.ProductCatalogService.class);
    final Instant now = Instant.parse("2026-11-10T00:00:00Z");
    final BehaviorService service = new BehaviorService(store,catalog,Clock.fixed(now,ZoneOffset.UTC));
    @Test void originalSearchPreservedButNormalizedForAnalytics() {
        var request = new BehaviorRequest(BehaviorType.SEARCH,null,"  ĐIỆN  thoại ",UUID.randomUUID(),null);
        when(store.retry(1L,request.clientEventId())).thenReturn(Optional.empty());
        when(store.insert(1L,request,"dien thoai",now)).thenReturn(true);
        assertThat(service.ingest(1L,request)).isTrue();
        verify(store).insert(1L,request,"dien thoai",now);
        verifyNoInteractions(catalog);
    }
    @Test void acceptedProductRetrySurvivesUnpublishing() {
        var product = UUID.randomUUID(); var key = UUID.randomUUID();
        var request = new BehaviorRequest(BehaviorType.PRODUCT_VIEW,product,null,key,null);
        when(store.retry(1L,key)).thenReturn(Optional.of(new BehaviorEvent(UUID.randomUUID(),BehaviorType.PRODUCT_VIEW,product,null,key,null,now)));
        assertThat(service.ingest(1L,request)).isFalse();
        verifyNoInteractions(catalog);
        verify(store,never()).insert(any(),any(),any(),any());
    }
    @Test void cleanupUsesFixedCutoffAndBoundedBatches() {
        var cutoff=now.minus(Duration.ofDays(90));
        when(store.cleanup(cutoff,1000)).thenReturn(1000,1000,3);
        new BehaviorRetention(store,Clock.fixed(now,ZoneOffset.UTC)).cleanup();
        verify(store,times(3)).cleanup(cutoff,1000);
    }
    @Test void oversizedWorkloadYieldsAfterHundredBatches() {
        when(store.cleanup(any(),eq(1000))).thenReturn(1000);
        new BehaviorRetention(store,Clock.fixed(now,ZoneOffset.UTC)).cleanup();
        verify(store,times(100)).cleanup(now.minus(Duration.ofDays(90)),1000);
    }
}
