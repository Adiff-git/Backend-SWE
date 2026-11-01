package com.example.demo.catalog.entity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariant {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Setter(AccessLevel.NONE)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@NotBlank
	@Column(name = "sku", nullable = false, columnDefinition = "text")
	private String sku;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "option_combination", nullable = false, columnDefinition = "jsonb")
	private Map<String, String> optionCombination;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "technical_specifications", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> technicalSpecifications = Map.of("schema_version", 1, "values", Map.of());

	@Column(name = "selling_price", nullable = false, columnDefinition = "numeric")
	private BigDecimal sellingPrice;
}
