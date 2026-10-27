package com.example.demo.catalog.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.demo.catalog.entity.ProductVariant;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

	List<ProductVariant> findByProduct_IdOrderByIdAsc(UUID productId);

	@Query("SELECT v FROM ProductVariant v WHERE v.product.id IN :productIds "
			+ "ORDER BY v.product.id, v.id")
	List<ProductVariant> findAllByProductIds(@Param("productIds") Collection<UUID> productIds);
}
