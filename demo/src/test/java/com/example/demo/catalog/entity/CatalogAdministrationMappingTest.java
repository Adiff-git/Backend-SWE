package com.example.demo.catalog.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Version;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

/** Mapping/default contracts without JPA startup. Real persistence proof is T022. */
class CatalogAdministrationMappingTest {
    @Test
    void revisions_shouldBeDatabaseOwned_withoutAnIndependentChildVersion() throws Exception {
        for (Class<?> type : new Class<?>[] {Product.class, Category.class, Brand.class, CatalogTaxonomyState.class}) {
            Column column = type.getDeclaredField("version").getAnnotation(Column.class);
            assertThat(column.updatable()).isFalse();
            assertThat(column.insertable()).isFalse();
            assertThat(Arrays.stream(type.getMethods()).map(java.lang.reflect.Method::getName)).doesNotContain("setVersion");
        }
        for (Class<?> type : new Class<?>[] {Product.class, ProductVariant.class, ProductImage.class}) {
            assertThat(Arrays.stream(type.getDeclaredFields()).filter(field -> field.isAnnotationPresent(Version.class))).isEmpty();
        }
    }

    @Test
    void newVariantAndImage_shouldMatchAdditiveDatabaseDefaults_andRetainSourceIdentity() throws Exception {
        assertThat(BeanUtils.instantiateClass(ProductVariant.class).isEnabled()).isTrue();
        assertThat(BeanUtils.instantiateClass(ProductImage.class).isActive()).isTrue();
        Field source = ProductImage.class.getDeclaredField("sourceImageId");
        assertThat(source.getAnnotation(Column.class).name()).isEqualTo("source_image_id");
        assertThat(source.getAnnotation(Column.class).nullable()).isTrue();
        assertThat(BeanUtils.instantiateClass(CatalogTaxonomyState.class).getId()).isEqualTo((short) 1);
        assertThat(ProductStatus.valueOf("UNPUBLISHED")).isEqualTo(ProductStatus.UNPUBLISHED);
    }
}
