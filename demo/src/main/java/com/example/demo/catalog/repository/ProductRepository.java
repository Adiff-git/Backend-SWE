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

public interface ProductRepository extends JpaRepository<Product, UUID> {

	@Query(value = "SELECT id FROM products WHERE status = 'PUBLISHED' ORDER BY id ASC",
			countQuery = "SELECT COUNT(*) FROM products WHERE status = 'PUBLISHED'", nativeQuery = true)
	Page<UUID> findPublishedIdPage(Pageable pageable);

	@Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id IN :ids AND p.status = :status")
	List<Product> findAllByIdsAndStatusWithCategory(@Param("ids") Collection<UUID> ids,
			@Param("status") ProductStatus status);

	/** One ordered lock statement avoids per-image relationship queries and lock inversion. */
	@Query(value = "SELECT id, status FROM products WHERE id IN (:ids) ORDER BY id ASC FOR SHARE", nativeQuery = true)
	List<Object[]> lockStatusesForShare(@Param("ids") Collection<UUID> ids);

	Optional<Product> findByIdAndStatus(UUID id, ProductStatus status);

	@Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id = :id AND p.status = :status")
	Optional<Product> findByIdAndStatusWithCategory(@Param("id") UUID id,
			@Param("status") ProductStatus status);

	@Query("SELECT p FROM Product p WHERE p.id IN :ids AND p.status = :status")
	List<Product> findAllByIdsAndStatus(@Param("ids") Collection<UUID> ids,
			@Param("status") ProductStatus status);

	/** Caller must hold a write-capable transaction and lock products in UUID order. */
	@Query(value = "SELECT status FROM products WHERE id = :id FOR SHARE", nativeQuery = true)
	Optional<String> lockStatusForShare(@Param("id") UUID id);

	/** Parent lock for variant/gallery writers; upload rows are locked afterward. */
	@Query(value = "SELECT id FROM products WHERE id = :id FOR NO KEY UPDATE", nativeQuery = true)
	Optional<UUID> lockIdForNoKeyUpdate(@Param("id") UUID id);
}
