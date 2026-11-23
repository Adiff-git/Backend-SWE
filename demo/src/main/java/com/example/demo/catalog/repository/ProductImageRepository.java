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

    @Query("SELECT i.product.id, i.objectKey FROM ProductImage i WHERE i.product.id IN :ids AND i.primary = true AND i.active = true")
    List<Object[]> findOrderPrimaryImages(@Param("ids") Collection<UUID> ids);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM product_variants v JOIN products p ON p.id=v.product_id
                JOIN product_images i ON i.product_id=p.id AND i.active AND i.is_primary
                WHERE v.id IN (:ids) AND p.status='PUBLISHED' AND v.enabled
                  AND v.selling_price::TEXT NOT IN ('NaN','Infinity','-Infinity')
                  AND v.selling_price>0 AND TRUNC(v.selling_price)=v.selling_price
            )
            """, nativeQuery = true)
    boolean hasCartImageCandidates(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT set_config('lock_timeout', :duration, TRUE)", nativeQuery = true)
    String setOrderReadLockTimeout(@Param("duration") String duration);

    @Query("SELECT i.product.id, i.objectKey FROM ProductImage i WHERE i.product.id IN :ids AND i.primary = true AND i.active = true AND i.product.status = com.example.demo.catalog.entity.ProductStatus.PUBLISHED")
    List<Object[]> findCartPrimaryImages(@Param("ids") Collection<UUID> ids);

	@Query("SELECT i FROM ProductImage i WHERE i.product.id IN :productIds AND i.primary = true AND i.active = true")
	List<ProductImage> findPrimaryImagesForProducts(@Param("productIds") Collection<UUID> productIds);

    @Query("SELECT i.id AS id,i.objectKey AS objectKey,i.altText AS altText,i.width AS width,i.height AS height,"
            + "i.displayOrder AS displayOrder,i.primary AS primaryImage FROM ProductImage i "
            + "WHERE i.product.id=:id AND i.active=true ORDER BY i.displayOrder,i.id")
    List<CatalogPublicViews.Image> findPublicGallery(@Param("id") UUID id);

	List<ProductImage> findByProduct_IdOrderByDisplayOrderAsc(UUID productId);

	Optional<ProductImage> findByProduct_IdAndPrimaryTrue(UUID productId);

    Optional<ProductImage> findByProduct_IdAndSourceImageId(UUID productId, UUID sourceImageId);

	long countByProduct_Id(UUID productId);

    boolean existsByProduct_IdAndActiveTrue(UUID productId);

    List<ProductImage> findByProduct_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(UUID productId);

	@Query("SELECT i FROM ProductImage i WHERE i.product.id IN :productIds "
			+ "ORDER BY i.product.id, i.displayOrder")
	List<ProductImage> findAllByProductIds(@Param("productIds") Collection<UUID> productIds);
}
