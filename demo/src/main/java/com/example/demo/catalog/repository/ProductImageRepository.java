package com.example.demo.catalog.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.demo.catalog.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

	@Query("SELECT i FROM ProductImage i WHERE i.product.id IN :productIds AND i.primary = true")
	List<ProductImage> findPrimaryImagesForProducts(@Param("productIds") Collection<UUID> productIds);

	List<ProductImage> findByProduct_IdOrderByDisplayOrderAsc(UUID productId);

	Optional<ProductImage> findByProduct_IdAndPrimaryTrue(UUID productId);

	long countByProduct_Id(UUID productId);

	boolean existsByProduct_IdAndDisplayOrder(UUID productId, int displayOrder);

	@Query("SELECT MAX(i.displayOrder) FROM ProductImage i WHERE i.product.id = :productId")
	Optional<Integer> findMaxDisplayOrder(@Param("productId") UUID productId);

	@Query("SELECT i FROM ProductImage i WHERE i.product.id IN :productIds "
			+ "ORDER BY i.product.id, i.displayOrder")
	List<ProductImage> findAllByProductIds(@Param("productIds") Collection<UUID> productIds);
}
