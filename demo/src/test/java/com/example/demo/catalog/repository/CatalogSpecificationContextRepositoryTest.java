package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.catalog.validator.TechnicalSpecificationValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogSpecificationContextRepositoryTest {
    @Test
    void context_shouldReuseAuthoritativeSqlAndBindCategory_withoutASecondTreeResolver() {
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        UUID id = UUID.randomUUID();
        when(em.createNativeQuery(CatalogSpecificationContextRepository.SQL)).thenReturn(query);
        when(query.setParameter("categoryId", id)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of("laptop,tablet"));
        CatalogSpecificationContextRepository repository = new CatalogSpecificationContextRepository(em);
        assertThat(repository.getApplicableFamilies(id)).containsExactlyInAnyOrder("laptop", "tablet");
        assertThat(CatalogSpecificationContextRepository.SQL).contains(CatalogApplicabilitySql.registryCtes());
        verify(query).setParameter("categoryId", id);
        when(query.getResultList()).thenReturn(List.of(""));
        assertThat(repository.getApplicableFamilies(id)).isEmpty();
        when(query.getResultList()).thenReturn(List.of());
        assertThatThrownBy(() -> repository.getApplicableFamilies(id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void persistedFacts_shouldRejectInvalidProposedApplicability_withoutChangingTheDocument() {
        TechnicalSpecificationValidator validator = new TechnicalSpecificationValidator();
        Map<String, Object> facts = Map.of("schema_version", 1, "values", Map.of("physical_ram", "generic"));
        assertThatCode(() -> validator.validatePersisted(facts, Set.of("tablet"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validatePersisted(facts, Set.of("laptop")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(facts.get("values")).isEqualTo(Map.of("physical_ram", "generic"));
    }
}
