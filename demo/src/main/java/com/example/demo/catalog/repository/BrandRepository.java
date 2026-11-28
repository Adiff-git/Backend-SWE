package com.example.demo.catalog.repository;

import com.example.demo.catalog.entity.Brand;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.example.demo.catalog.dto.response.BrandListItem;
import java.util.List;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
    Optional<Brand> findByCode(String code);

    @Query("""
            select new com.example.demo.catalog.dto.response.BrandListItem(b.id, b.code, b.name, count(p.id))
            from Brand b left join Product p on p.brand = b
                and p.status = com.example.demo.catalog.entity.ProductStatus.PUBLISHED
            group by b.id, b.code, b.name
            order by b.name, b.code
            """)
    List<BrandListItem> findAllWithProductCounts();
}
