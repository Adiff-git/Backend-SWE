package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.example.demo.catalog.dto.request.AdminGalleryRequests.Attach;
import com.example.demo.catalog.dto.request.AdminProductRequests;
import com.example.demo.catalog.dto.request.AdminVariantRequests;
import com.example.demo.catalog.dto.request.CatalogField;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.media.integration.SeededMediaTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import software.amazon.awssdk.core.sync.RequestBody;

/** Real storage/DB commits; signer spy is used only for the explicit dependency-failure case. */
@SpringBootTest
class AdminGalleryServiceIT extends SeededMediaTestSupport {
    @Autowired AdminGalleryService gallery;
    @Autowired AdminProductService products;
    @Autowired AdminVariantService variants;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean ProductImageStorage signing;
    private UUID product;
    private Long actor;
    @BeforeEach void prepareDraft(){
        jdbc.execute("TRUNCATE users,categories,brands RESTART IDENTITY CASCADE");
        actor=owner();UUID category=UUID.randomUUID();jdbc.update("INSERT INTO categories(id,name) VALUES (?,'Laptop')",category);
        product=products.create(new AdminProductRequests.Create("Gallery",category,CatalogField.absent(),null,CatalogField.absent())).getId();
    }
    private long version(){return jdbc.queryForObject("SELECT version FROM products WHERE id=?",Long.class,product);}
    private long taxonomy(){return jdbc.queryForObject("SELECT version FROM catalog_taxonomy_state WHERE id=1",Long.class);}
    private UUID source(int color) throws Exception {
        var upload=prepare(actor);BufferedImage pixels=new BufferedImage(12,12,BufferedImage.TYPE_INT_RGB);pixels.setRGB(0,0,color);
        ByteArrayOutputStream data=new ByteArrayOutputStream();ImageIO.write(pixels,"png",data);
        String staging=images.findById(upload.getId()).orElseThrow().getStagingKey();
        s3.putObject(r->r.bucket(bucket).key(staging).contentType("image/png"),RequestBody.fromBytes(data.toByteArray()));
        return media.completeImageUpload(actor,upload.getId()).getId();
    }
    private com.example.demo.catalog.dto.response.AdminCatalogResponses.Attachment attach(UUID source,Integer order){
        return gallery.attach(product,actor,new Attach(version(),taxonomy(),source,"Ready",null,order));
    }
    @Test void primaryRemovalAndExtremeReorder_shouldRetainValidPublishedGallery() throws Exception {
        var first=attach(source(1),Integer.MAX_VALUE);var second=attach(source(2),0);
        UUID a=first.getImage().getId(),b=second.getImage().getId();
        gallery.reorder(product,version(),taxonomy(),List.of(b,a));
        assertThat(gallery.get(product).getItems()).extracting(i->i.getDisplayOrder()).containsExactly(0,1);
        var d=products.get(product);variants.create(product,new AdminVariantRequests.Create(Long.parseLong(d.getVersion()),Long.parseLong(d.getTaxonomyVersion()),"GALLERY",BigDecimal.TEN,Map.of(),Map.of("schema_version",1,"values",Map.of()),true));
        products.transition(product,version(),taxonomy(),AdminProductRequests.Action.PUBLISH);long before=version();
        assertThatThrownBy(()->gallery.remove(product,a,before,taxonomy(),null)).isInstanceOf(AdminCatalogException.class);
        assertThat(version()).isEqualTo(before);
        gallery.remove(product,a,version(),taxonomy(),b);
        assertThat(gallery.get(product).getItems()).singleElement().satisfies(i->{assertThat(i.getId()).isEqualTo(b);assertThat(i.isPrimary()).isTrue();});
        assertThat(jdbc.queryForObject("SELECT active FROM product_images WHERE id=?",Boolean.class,a)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_images",Integer.class)).isEqualTo(2);
    }
    @Test void legacyNullSourceAndPublishedUploadFk_shouldSurviveLogicalDetach() throws Exception {
        var attached=attach(source(3),null);UUID image=attached.getImage().getId(),upload=UUID.randomUUID();
        String key=jdbc.queryForObject("SELECT object_key FROM product_images WHERE id=?",String.class,image);
        jdbc.update("UPDATE product_images SET source_image_id=NULL WHERE id=?",image);
        jdbc.update("INSERT INTO product_image_uploads(id,product_id,preparer_subject,staging_key,durable_key,proposed_content_type,proposed_byte_size,alt_text,state,prepared_at,expires_at,published_at,product_image_id) VALUES (?,?,?,'synthetic-legacy-stage',?,'image/png',100,'Legacy','PUBLISHED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 day',CURRENT_TIMESTAMP,?)",upload,product,actor.toString(),key,image);
        gallery.remove(product,image,version(),taxonomy(),null);
        assertThat(jdbc.queryForObject("SELECT product_image_id FROM product_image_uploads WHERE id=?",UUID.class,upload)).isEqualTo(image);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_images WHERE id=? AND NOT active",Integer.class,image)).isOne();
        assertThat(s3.headObject(r->r.bucket(bucket).key(key)).contentLength()).isPositive();
    }
    @Test void tenImageLimit_shouldRejectEleventhWithoutConsumingRevision() throws Exception {
        for(int n=1;n<=10;n++)attach(source(n),null);
        UUID overflow=source(11);long before=version();
        assertThatThrownBy(()->attach(overflow,null)).isInstanceOf(AdminCatalogException.class);
        assertThat(version()).isEqualTo(before);assertThat(gallery.get(product).getItems()).hasSize(10);
        assertThat(media.validateImageForAttachment(actor,overflow).getId()).isEqualTo(overflow);
    }
    @Test void copiedObjectAfterDeferredFailure_shouldRemainPrivateAndRevisionMustRollBack() throws Exception {
        UUID source=source(12);long before=version();int count=s3.listObjectsV2(r->r.bucket(bucket).prefix("images/")).contents().size();
        jdbc.execute("CREATE FUNCTION admin013_test_late() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic late failure' USING ERRCODE='23514'; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER admin013_test_late AFTER INSERT ON product_images DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION admin013_test_late()");
        try{
            assertThatThrownBy(()->attach(source,null)).isInstanceOf(RuntimeException.class);
            assertThat(version()).isEqualTo(before);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_images",Integer.class)).isZero();
            assertThat(s3.listObjectsV2(r->r.bucket(bucket).prefix("images/")).contents().size()).isGreaterThan(count);
            assertThat(media.validateImageForAttachment(actor,source).getId()).isEqualTo(source);
        }finally{jdbc.execute("DROP TRIGGER admin013_test_late ON product_images");jdbc.execute("DROP FUNCTION admin013_test_late()");}
    }
    @Test void emptyGalleryNeedsNoSigner_butNonemptyGalleryMustFailClosedOnSignerOutage() throws Exception {
        doThrow(new IllegalStateException("Synthetic signer outage")).when(signing).prepareReadSigner();
        assertThat(gallery.get(product).getItems()).isEmpty();reset(signing);
        attach(source(13),null);long before=version();
        doThrow(new IllegalStateException("Synthetic signer outage")).when(signing).prepareReadSigner();
        assertThatThrownBy(()->gallery.get(product)).isInstanceOf(AdminCatalogException.class).satisfies(failure->assertThat(((AdminCatalogException)failure).getStatus()).isEqualTo(503));
        assertThat(version()).isEqualTo(before);
    }
    @Test void completedSourceWithInvalidStoredTypeSizeOrBytes_shouldNotBecomeGalleryEligible() throws Exception {
        UUID source=source(14);String key=images.findById(source).orElseThrow().getFinalKey();
        byte[][] contents={com.example.demo.media.testdata.MediaTestData.fixture("valid.png"),
                new byte[com.example.demo.catalog.validator.ProductImageContentValidator.MAX_BYTES+1],new byte[]{1,2,3}};
        for(int n=0;n<contents.length;n++){
            String type=n==0?"image/jpeg":"image/png";byte[] bytes=contents[n];
            s3.putObject(r->r.bucket(bucket).key(key).contentType(type),RequestBody.fromBytes(bytes));
            assertThatThrownBy(()->attach(source,null)).isInstanceOf(com.example.demo.catalog.exception.InvalidProductImageException.class);
            assertThat(version()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_images",Integer.class)).isZero();
        }
    }

}
