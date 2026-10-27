package com.example.demo.catalog.service;

import com.example.demo.catalog.converter.ProductCatalogConverter;
import com.example.demo.catalog.dto.response.ProductPage;
import com.example.demo.catalog.dto.response.ProductDetail;
import com.example.demo.catalog.dto.response.ProductSummary;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.entity.ProductVariant;
import com.example.demo.catalog.exception.CatalogConflictException;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.catalog.exception.ProductNotFoundException;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.repository.ProductVariantRepository;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductCatalogServiceImpl implements ProductCatalogService {

	private final ProductRepository products;
	private final ProductImageRepository images;
	private final ProductVariantRepository variants;
	private final ProductImageStorage imageStorage;
	private final ProductCatalogConverter converter;

	@Override
	@Transactional // FOR SHARE URL eligibility requires a write-capable transaction.
	public ProductPage listProducts(int page, int size) {
		validatePage(page, size);
		Page<UUID> idPage = products.findPublishedIdPage(PageRequest.of(page, size));
		List<UUID> ids = idPage.getContent();
		if (ids.isEmpty()) {
			return page(List.of(), page, size, idPage);
		}

		// Batch image metadata first, then recheck publication immediately before projection.
		List<ProductImage> primaryImages = images.findPrimaryImagesForProducts(ids);
		Map<UUID, Product> eligible = indexProducts(
				products.findAllByIdsAndStatusWithCategory(ids, ProductStatus.PUBLISHED));
		Map<UUID, ProductImage> primaryByProduct = new HashMap<>();
		for (ProductImage image : primaryImages) {
			UUID productId = image.getProduct().getId();
			if (eligible.containsKey(productId)) {
				primaryByProduct.put(productId, image);
			}
		}

		Map<UUID, String> signedUrls = new HashMap<>();
		if (!primaryByProduct.isEmpty()) {
			// Credential resolution happens before taking locks. Signing itself is local.
			try (PreparedReadSigner signer = imageStorage.prepareReadSigner()) {
				Set<UUID> lockedPublished = lockPublishedImageProducts(primaryByProduct.keySet());
				for (UUID id : ids) {
					if (lockedPublished.contains(id)) {
						signedUrls.put(id, signer.signReadUrl(primaryByProduct.get(id).getObjectKey()));
					} else if (primaryByProduct.containsKey(id)) {
						eligible.remove(id);
					}
				}
			}
		}

		List<ProductSummary> summaries = new ArrayList<>();
		for (UUID id : ids) {
			Product product = eligible.get(id);
			if (product != null) {
				summaries.add(converter.toSummary(product, signedUrls.get(id)));
			}
		}
		return page(summaries, page, size, idPage);
	}

	@Override
	@Transactional // A gallery read may need FOR SHARE before local URL signing.
	public ProductDetail getProduct(UUID id) {
		Product product = products.findByIdAndStatusWithCategory(id, ProductStatus.PUBLISHED)
				.orElseThrow(ProductNotFoundException::new);
		List<ProductVariant> ownedVariants = variants.findByProduct_IdOrderByIdAsc(id);
		if (ownedVariants.isEmpty()) {
			throw new CatalogConflictException();
		}
		List<ProductImage> gallery = images.findByProduct_IdOrderByDisplayOrderAsc(id);
		Map<UUID, String> urls = new HashMap<>();
		if (gallery.isEmpty()) {
			// Recheck after the relationship reads, immediately before DTO projection.
			product = products.findByIdAndStatusWithCategory(id, ProductStatus.PUBLISHED)
					.orElseThrow(ProductNotFoundException::new);
		} else {
			// Resolve credentials before locking; signing performs no S3 network operation.
			try (PreparedReadSigner signer = imageStorage.prepareReadSigner()) {
				String status = products.lockStatusForShare(id).orElseThrow(ProductNotFoundException::new);
				if (!ProductStatus.PUBLISHED.name().equals(status)) {
					throw new ProductNotFoundException();
				}
				for (ProductImage image : gallery) {
					urls.put(image.getId(), signer.signReadUrl(image.getObjectKey()));
				}
			}
		}
		return converter.toDetail(product, ownedVariants, gallery, urls);
	}

	private void validatePage(int page, int size) {
		if (page < 0) {
			throw new InvalidCatalogRequestException("Invalid page parameter");
		}
		if (size < 1 || size > MAX_SIZE) {
			throw new InvalidCatalogRequestException("Invalid size parameter");
		}
	}

	private Map<UUID, Product> indexProducts(List<Product> fetched) {
		Map<UUID, Product> byId = new HashMap<>();
		for (Product product : fetched) {
			byId.put(product.getId(), product);
		}
		return byId;
	}

	private Set<UUID> lockPublishedImageProducts(Collection<UUID> imageProductIds) {
		Set<UUID> published = new HashSet<>();
		// The PostgreSQL query orders UUIDs before taking row locks.
		for (Object[] row : products.lockStatusesForShare(imageProductIds)) {
			if (ProductStatus.PUBLISHED.name().equals(row[1])) {
				published.add((UUID) row[0]);
			}
		}
		return published;
	}

	private ProductPage page(List<ProductSummary> summaries, int page, int size, Page<UUID> idPage) {
		return new ProductPage(List.copyOf(summaries), page, size,
				idPage.getTotalElements(), idPage.getTotalPages());
	}
}
