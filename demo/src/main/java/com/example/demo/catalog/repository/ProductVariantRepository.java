package com.example.demo.catalog.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.demo.catalog.entity.ProductVariant;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    @Query("SELECT v.id AS variantId, p.id AS productId, p.status AS status, v.sellingPrice AS price, "
            + "p.name AS productName, v.sku AS sku, v.optionCombination AS options "
            + "FROM ProductVariant v JOIN v.product p WHERE v.id IN :ids")
    List<CartVariantView> findCartStates(@Param("ids") Collection<UUID> ids);

	List<ProductVariant> findByProduct_IdOrderByIdAsc(UUID productId);

	@Query("SELECT v FROM ProductVariant v WHERE v.product.id IN :productIds "
			+ "ORDER BY v.product.id, v.id")
	List<ProductVariant> findAllByProductIds(@Param("productIds") Collection<UUID> productIds);
}
