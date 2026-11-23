package com.example.demo.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Database-seeded gate; never create/reset a revision during a request. */
@Entity
@Table(name = "catalog_taxonomy_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogTaxonomyState {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private short id = 1;

    @Column(name = "version", nullable = false, insertable = false, updatable = false)
    private long version;
}
