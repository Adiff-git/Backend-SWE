package com.example.demo.catalog.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, UUID>, ProductSearchRepository, AdminProductRepository {

	@Query(value = "SELECT id FROM products WHERE status = 'PUBLISHED' ORDER BY id ASC",
			countQuery = "SELECT COUNT(*) FROM products WHERE status = 'PUBLISHED'", nativeQuery = true)
	Page<UUID> findPublishedIdPage(Pageable pageable);

	@Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id IN :ids AND p.status = :status")
	List<Product> findAllByIdsAndStatusWithCategory(@Param("ids") Collection<UUID> ids,
			@Param("status") ProductStatus status);

	/** One ordered lock statement avoids per-image relationship queries and lock inversion. */
	@Query(value = "SELECT id, status FROM products WHERE id IN (:ids) ORDER BY id ASC FOR SHARE", nativeQuery = true)
	List<Object[]> lockStatusesForShare(@Param("ids") Collection<UUID> ids);

    @Query("SELECT p.id AS id,p.name AS name,p.description AS description,c.id AS categoryId,c.name AS categoryName,"
            + "b.id AS brandId,b.code AS brandCode,b.name AS brandName FROM Product p JOIN p.category c LEFT JOIN p.brand b "
            + "WHERE p.id IN :ids AND p.status=com.example.demo.catalog.entity.ProductStatus.PUBLISHED")
    List<CatalogPublicViews.Product> findPublicSummaries(@Param("ids") Collection<UUID> ids);

    @Query(value="SELECT p.id,p.name,p.description,c.id AS \"categoryId\",c.name AS \"categoryName\","
            + "b.id AS \"brandId\",b.code AS \"brandCode\",b.name AS \"brandName\" "
            + "FROM products p JOIN categories c ON c.id=p.category_id LEFT JOIN brands b ON b.id=p.brand_id "
            + "WHERE p.id=:id AND p.status='PUBLISHED' FOR SHARE OF p",nativeQuery=true)
    Optional<CatalogPublicViews.Product> findPublishedForShare(@Param("id") UUID id);

	Optional<Product> findByIdAndStatus(UUID id, ProductStatus status);

	@Query("SELECT p FROM Product p JOIN FETCH p.category LEFT JOIN FETCH p.brand WHERE p.id = :id AND p.status = :status")
	Optional<Product> findByIdAndStatusWithCategory(@Param("id") UUID id,
			@Param("status") ProductStatus status);

	@Query("SELECT p FROM Product p WHERE p.id IN :ids AND p.status = :status")
	List<Product> findAllByIdsAndStatus(@Param("ids") Collection<UUID> ids,
			@Param("status") ProductStatus status);

	/** Caller must hold a write-capable transaction and lock products in UUID order. */
	@Query(value = "SELECT status FROM products WHERE id = :id FOR SHARE", nativeQuery = true)
	Optional<String> lockStatusForShare(@Param("id") UUID id);

}
