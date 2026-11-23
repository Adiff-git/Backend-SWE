package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.dto.request.AdminProductRequests;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Committed public-read/Admin interleaving; signing is local instrumentation, not S3 proof. */
class AdminCatalogPublicEligibilityIT extends AdminCatalogIntegrationSupport {
    @Autowired ProductCatalogService publicCatalog;
    @Autowired AdminGalleryService gallery;

    @Test void detachedPrimaryMustNotBeSignedFromPreLockProjection() throws Exception {
        var product=ready();
        var original=products.get(product).getImages().getFirst().getId();
        var replacement=image(product,1,false);
        var current=products.get(product);
        var originalKey=jdbc.queryForObject("SELECT object_key FROM product_images WHERE id=?",String.class,original);
        var replacementKey=jdbc.queryForObject("SELECT object_key FROM product_images WHERE id=?",String.class,replacement);
        CountDownLatch preparing=new CountDownLatch(1),resume=new CountDownLatch(1);
        testSigner.signedKeys.clear();
        testSigner.beforePrepare=()->{preparing.countDown();try{assertThat(resume.await(15,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}};
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()){
            var read=executor.submit(()->publicCatalog.getProduct(product));
            try{
                assertThat(preparing.await(10,TimeUnit.SECONDS)).isTrue();
                gallery.remove(product,original,version(current),taxonomy(current),replacement);
            }finally{resume.countDown();}
            var detail=read.get(15,TimeUnit.SECONDS);
            assertThat(detail.getImages()).extracting(i->i.getId()).containsExactly(replacement);
            assertThat(testSigner.signedKeys).contains(replacementKey).doesNotContain(originalKey);
        }finally{resume.countDown();testSigner.reset();}
        assertThat(version(products.get(product))).isEqualTo(version(current)+1);
    }

    @Test void disabledAndWithdrawnProductsMustBeExcludedAndRepublishedStateReloaded(){
        var product=ready();
        var first=products.get(product).getVariants().getFirst().getId();
        var second=variant(product,"PUBLIC-SECOND","white",BigDecimal.valueOf(2000));
        var current=products.get(product);
        variants.setEnabled(product,first,version(current),taxonomy(current),false);
        assertThat(publicCatalog.getProduct(product).getVariants()).extracting(v->v.getId()).containsExactly(second);
        transition(product,AdminProductRequests.Action.UNPUBLISH);
        assertThat(publicCatalog.getProductSummaries(java.util.List.of(product))).isEmpty();
        transition(product,AdminProductRequests.Action.ARCHIVE);
        assertThat(publicCatalog.getProductSummaries(java.util.List.of(product))).isEmpty();
        transition(product,AdminProductRequests.Action.RESTORE);
        assertThat(publicCatalog.getProductSummaries(java.util.List.of(product))).isEmpty();
        transition(product,AdminProductRequests.Action.PUBLISH);
        assertThat(publicCatalog.getProduct(product).getVariants()).extracting(v->v.getId()).containsExactly(second);
        assertThat(publicCatalog.getProductSummaries(java.util.List.of(product))).containsKey(product);
    }
}
