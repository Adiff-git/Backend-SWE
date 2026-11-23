package com.example.demo.catalog.audit;

import static com.example.demo.catalog.audit.V27AuditDatabase.execute;
import static com.example.demo.catalog.audit.V27AuditDatabase.scalar;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Explicit operator preparation, never test discovery or an application bootstrap.
 * Accepts only a private registry for newly owned Compose projects and verifies labels/IDs.
 * Does not expose any target migration action. */
public final class CatalogDemoPreparation {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Map<String,Object> registry;
    private final Path directory;
    CatalogDemoPreparation(Path file) throws Exception {
        registry=JSON.readValue(Files.readString(file),new TypeReference<Map<String,Object>>(){});
        directory=Path.of((String)registry.get("directory"));
        assertThat(registry.get("scope")).isEqualTo("AUTHORIZED_NEW_LOCAL_SYNTHETIC_DEMO_ONLY");
        assertThat((String)registry.get("run")).matches("admin013-[a-f0-9]{8}");
        for(String kind:List.of("reference","demo","copy","storage"))verifyOwnership(kind);
    }
    @SuppressWarnings("unchecked") Map<String,Object> environment(String name){
        return (Map<String,Object>)((Map<String,Object>)registry.get("environments")).get(name);
    }
    void verifyOwnership(String name) throws Exception {
        Map<String,Object> env=environment(name);
        String project=registry.get("run")+"-"+name;
        assertThat(env.get("project")).isEqualTo(project);
        for(Object id:(List<?>)env.get("containerIds")){
            assertThat(id.toString()).matches("[a-f0-9]{64}");
            Process process=new ProcessBuilder("docker","inspect","--format","{{index .Config.Labels \"com.docker.compose.project\"}}",id.toString()).start();
            assertThat(new String(process.getInputStream().readAllBytes()).strip()).isEqualTo(project);
            assertThat(process.waitFor()).isZero();
        }
    }
    String url(String name){return "jdbc:postgresql://127.0.0.1:"+environment(name).get("port")+"/"+environment(name).get("database");}
    Connection owner(String name) throws Exception {
        Map<String,Object> env=environment(name);
        return DriverManager.getConnection(url(name),(String)env.get("username"),(String)env.get("password"));
    }
    private Connection reader(String name) throws java.sql.SQLException {
        return DriverManager.getConnection(url(name),"admin013_reader",(String)environment(name).get("password"));
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2 || !List.of("initialize-reference","complete-reference-fixture","audit").contains(args[0]))throw new IllegalArgumentException("Explicit preparation action and owned registry required");
        CatalogDemoPreparation preparation=new CatalogDemoPreparation(Path.of(args[1]));
        if(args[0].equals("initialize-reference"))preparation.initializeReference();else if(args[0].equals("complete-reference-fixture"))preparation.completeReferenceFixture();else preparation.audit();
    }
    private void initializeReference() throws Exception {
        try(Connection connection=owner("reference")){
            if(!scalar(connection,"SELECT COUNT(*) FROM pg_tables WHERE schemaname='public'").equals("0")){
                assertThat(scalar(connection,"SELECT MAX(version::int) FROM flyway_schema_history WHERE success")).isEqualTo("27");
                assertThat(scalar(connection,"SELECT COUNT(*) FROM products")).isEqualTo("0");
            }
        }
        Map<String,Object> env=environment("reference");
        Flyway.configure().dataSource(url("reference"),(String)env.get("username"),(String)env.get("password")).target("27").load().migrate();
        BufferedImage image=new BufferedImage(12,12,BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);byte[] bytes=output.toByteArray();
        Files.write(directory.resolve("synthetic.png"),bytes);
        String bucket=registry.get("run")+"-images";
        try(var presigner=software.amazon.awssdk.services.s3.presigner.S3Presigner.builder().region(Region.US_EAST_1)
                .endpointOverride(java.net.URI.create("http://127.0.0.1:"+environment("storage").get("port")))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).checksumValidationEnabled(false).build())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test","test"))).build()){
            for(int n=1;n<=3;n++){
                String key="images/synthetic/"+n+".png";
                var signed=presigner.presignPutObject(r->r.signatureDuration(java.time.Duration.ofMinutes(5))
                        .putObjectRequest(p->p.bucket(bucket).key(key).contentType("image/png")));
                var request=java.net.http.HttpRequest.newBuilder(signed.url().toURI()).header("Content-Type","image/png")
                        .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
                assertThat(java.net.http.HttpClient.newHttpClient().send(request,java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
            }
        }
        try(Connection connection=owner("reference")){
            seed(connection,bytes.length);
            List<String> manifest=new ArrayList<>();
            try(Statement query=connection.createStatement();var rows=query.executeQuery("SELECT version,checksum,script FROM flyway_schema_history ORDER BY installed_rank")){
                while(rows.next())manifest.add(rows.getString(1)+"\t"+rows.getString(2)+"\t"+rows.getString(3));
            }
            Files.writeString(directory.resolve("v27.tsv"),String.join("\n",manifest)+"\n");
            Files.writeString(directory.resolve("reference-fingerprint.txt"),V27AuditDatabase.fingerprint(connection));
        }
        System.out.println("SYNTHETIC_REFERENCE_INITIALIZED_V27");
    }
    private static UUID id(String key){return UUID.nameUUIDFromBytes(("admin013-synthetic-"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private void seed(Connection db,int size) throws Exception {
        db.setAutoCommit(false);
        try{
            execute(db,"INSERT INTO categories(id,name) VALUES (?,'Laptop'),(?,'Tablet'),(?,'Accessories')",id("laptop"),id("tablet"),id("accessories"));
            execute(db,"INSERT INTO categories(id,name,parent_category_id) VALUES (?,'Portable',?),(?,'Portable',?)",id("laptop-child"),id("laptop"),id("tablet-child"),id("tablet"));
            execute(db,"INSERT INTO brands(id,code,name) VALUES (?,'synthetic-brand','Synthetic Brand')",id("brand"));
            execute(db,"INSERT INTO users(username,password,account_status,joined_at) VALUES ('synthetic-demo-customer','not-a-login-credential','ACTIVE','2026-01-01T00:00:00Z')");
            long user=Long.parseLong(scalar(db,"SELECT id FROM users WHERE username='synthetic-demo-customer'"));
            for(int n=1;n<=4;n++){
                UUID product=id("product"+n),variant=id("variant"+n);
                execute(db,"INSERT INTO products(id,category_id,brand_id,name,description,status) VALUES (?,?,?,?,'Synthetic demonstration record',?)",product,n==2?id("tablet"):id("laptop-child"),n==4?null:id("brand"),"Synthetic product "+n,n==3?"DRAFT":n==4?"ARCHIVED":"PUBLISHED");
                execute(db,"INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price,technical_specifications) VALUES (?,?,?,?::jsonb,?,?::jsonb)",variant,product,"DEMO-SYNTH-"+n,"{\"model\":\""+n+"\"}",n==3?0:1000*n,"{\"schema_version\":1,\"values\":{\"physical_ram\":{\"value\":8,\"unit\":\"GB\"}}}");
                if(n<=2)execute(db,"INSERT INTO product_images(id,product_id,object_key,alt_text,content_type,byte_size,width,height,display_order,is_primary,created_at) VALUES (?,?,?,'Synthetic demonstration image','image/png',?,12,12,0,TRUE,'2026-01-01T00:00:00Z')",id("image"+n),product,"images/synthetic/"+n+".png",size);
                execute(db,"UPDATE inventory_balances SET stock=100 WHERE product_variant_id=?",variant);
            }
            execute(db,"INSERT INTO carts(user_id) VALUES (?)",user);long cart=Long.parseLong(scalar(db,"SELECT id FROM carts"));
            execute(db,"INSERT INTO cart_items(cart_id,product_id,product_variant_id,quantity,selected,added_at,public_product_name,public_sku,public_options) VALUES (?,?,?,2,TRUE,'2026-01-01T00:00:00Z','Synthetic cart snapshot','DEMO-SYNTH-1','{\"model\":\"1\"}')",cart,id("product1"),id("variant1"));
            for(int n=1;n<=4;n++){
                boolean managed=n<4;int amount=n==4?0:1000;String number="SYNTHETIC-ORDER-"+n;
                execute(db,"INSERT INTO orders(customer_id,order_number,placed_at,status,recipient_name_snapshot,recipient_phone_snapshot,delivery_address_snapshot,merchandise_total,payable_total,inventory_managed) VALUES (?,?,'2026-01-01T00:00:00Z','PENDING','Synthetic recipient','000','Synthetic address',?,?,?)",user,number,amount,amount,managed);
                long order=Long.parseLong(scalar(db,"SELECT id FROM orders WHERE order_number='"+number+"'"));
                execute(db,"INSERT INTO order_items(order_id,product_id,product_variant_id,product_name_snapshot,variant_sku_snapshot,variant_options_snapshot,quantity,unit_price_snapshot,line_total) VALUES (?,?,?,'Historical synthetic product','Historical SKU','{\"model\":\"1\"}',1,?,?)",order,id("product1"),id("variant1"),amount,amount);
                if(managed){
                    execute(db,"INSERT INTO inventory_reservations(order_id,order_item_id,inventory_balance_id,original_variant_id,quantity,state,reserved_at) SELECT ?,i.id,b.id,?,1,'ACTIVE',o.placed_at FROM order_items i JOIN orders o ON o.id=i.order_id CROSS JOIN inventory_balances b WHERE i.order_id=? AND b.product_variant_id=?",order,id("variant1"),order,id("variant1"));
                    if(n==2 || n==3){
                        execute(db,"UPDATE orders SET status=?,completed_at=?::timestamptz,cancelled_at=?::timestamptz,cancellation_reason=? WHERE id=?",n==2?"COMPLETED":"CANCELLED",n==2?"2026-01-02T00:00:00Z":null,n==3?"2026-01-02T00:00:00Z":null,n==3?"Synthetic cancellation":null,order);
                        execute(db,"UPDATE inventory_reservations SET state=?,resolved_at='2026-01-02T00:00:00Z' WHERE order_id=?",n==2?"CONSUMED":"RELEASED",order);
                    }
                }
            }
            execute(db,"UPDATE inventory_balances SET reserved=1 WHERE product_variant_id=?",id("variant1"));
            String hash="a".repeat(64);
            execute(db,"INSERT INTO catalog_source_links(id,source,source_key,source_url,product_variant_id,source_payload_sha256,normalized_content_sha256,normalization_version,approved_manifest_sha256,updated_at) VALUES (?,'synthetic','demo-1','https://example.invalid/synthetic',?,?,?,'synthetic-v1',?,'2026-01-01T00:00:00Z')",id("source"),id("variant1"),hash,hash,hash);
            db.commit();
        }catch(Exception failure){db.rollback();throw failure;}finally{db.setAutoCommit(true);}
    }
    private void completeReferenceFixture() throws Exception {
        try(Connection db=owner("reference")){
            assertThat(scalar(db,"SELECT MAX(version::int) FROM flyway_schema_history")).isEqualTo("27");
            assertThat(scalar(db,"SELECT COUNT(*) FROM checkout_receipts")).isEqualTo("0");
            db.setAutoCommit(false);
            try{
                execute(db,"INSERT INTO image_uploads(id,owner_user_id,staging_key,final_key,content_type,status,expires_at,created_at) SELECT ?,id,'staging/synthetic','images/synthetic/2.png','image/png','COMPLETED','2026-01-02T00:00:00Z','2026-01-01T00:00:00Z' FROM users WHERE username='synthetic-demo-customer'",id("upload"));
                execute(db,"UPDATE product_images SET source_image_id=? WHERE id=?",id("upload"),id("image2"));
                execute(db,"""
                    INSERT INTO checkout_receipts(customer_id,operation,idempotency_key,request_address_id,request_fingerprint,order_id,receipt_version,response_status,location,receipt_body,created_at)
                    SELECT o.customer_id,'CHECKOUT','synthetic_demo_receipt_'||o.id,1,REPEAT('a',64),o.id,1,201,'/api/me/orders/'||o.id,
                      JSONB_BUILD_OBJECT('order',JSONB_BUILD_OBJECT('id',o.id,'orderNumber',o.order_number,'placedAt',o.placed_at,'status',o.status,
                        'recipientNameSnapshot',o.recipient_name_snapshot,'recipientPhoneSnapshot',o.recipient_phone_snapshot,'deliveryAddressSnapshot',o.delivery_address_snapshot,
                        'items',(SELECT JSONB_AGG(JSONB_BUILD_OBJECT('id',i.id,'productNameSnapshot',i.product_name_snapshot,'variantSkuSnapshot',i.variant_sku_snapshot,
                          'variantOptionsSnapshot',i.variant_options_snapshot,'quantity',i.quantity,'unitPriceSnapshot',JSONB_BUILD_OBJECT('amount',i.unit_price_snapshot,'currency','VND'),
                          'lineTotal',JSONB_BUILD_OBJECT('amount',i.line_total,'currency','VND')) ORDER BY i.id) FROM order_items i WHERE i.order_id=o.id),
                        'merchandiseTotal',JSONB_BUILD_OBJECT('amount',o.merchandise_total,'currency','VND'),'payableTotal',JSONB_BUILD_OBJECT('amount',o.payable_total,'currency','VND')),
                        'skippedItems','[]'::JSONB),o.placed_at FROM orders o WHERE o.status='PENDING'
                    """);
                db.commit();
            }catch(Exception failure){db.rollback();throw failure;}finally{db.setAutoCommit(true);}
            Files.writeString(directory.resolve("reference-fingerprint.txt"),V27AuditDatabase.fingerprint(db));
        }
        System.out.println("SYNTHETIC_REFERENCE_FIXTURE_COMPLETE");
    }
    private void audit() throws Exception {
        for(String name:List.of("reference","demo","copy")){
            // Preparation grants affect only newly created local resources, before auditing.
            try(Connection db=owner(name)){
                String password=(String)environment(name).get("password");
                assertThat(password).matches("[A-Za-z0-9_-]+");
                if(scalar(db,"SELECT COUNT(*) FROM pg_roles WHERE rolname='admin013_reader'").equals("0")){
                    execute(db,"CREATE ROLE admin013_reader LOGIN PASSWORD '"+password+"' NOSUPERUSER NOCREATEDB NOCREATEROLE");
                    execute(db,"GRANT USAGE ON SCHEMA public TO admin013_reader");execute(db,"GRANT SELECT ON ALL TABLES IN SCHEMA public TO admin013_reader");
                }
            }
            String before;
            try(Connection db=owner(name)){
                before=V27AuditDatabase.fingerprint(db);
                assertThat(before).isEqualTo(Files.readString(directory.resolve("reference-fingerprint.txt")));
                Map<String,String> identity=new LinkedHashMap<>();
                for(String key:List.of("server_version","server_encoding"))identity.put(key,scalar(db,"SHOW "+key));
                identity.put("collation",scalar(db,"SELECT datcollate FROM pg_database WHERE datname=CURRENT_DATABASE()"));
                identity.put("ctype",scalar(db,"SELECT datctype FROM pg_database WHERE datname=CURRENT_DATABASE()"));
                identity.put("database",scalar(db,"SELECT CURRENT_DATABASE()"));identity.put("containerId",((List<?>)environment(name).get("containerIds")).getFirst().toString());
                identity.put("schemaColumns",scalar(db,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public'"));
                identity.put("constraints",scalar(db,"SELECT COUNT(*) FROM pg_constraint x JOIN pg_namespace n ON n.oid=x.connamespace WHERE n.nspname='public'"));
                Files.writeString(directory.resolve(name+"-identity.json"),JSON.writeValueAsString(identity));
            }
            for(String script:List.of("data-preflight.sql","data-preflight-allocation-integrity.sql")){
                try(Connection db=reader(name)){
                    var results=V27AuditDatabase.script(db,Path.of("../specs/013-admin-catalog-management/contracts/"+script));
                    Files.writeString(directory.resolve(name+"-"+script+".json"),JSON.writeValueAsString(results));
                }
            }
            Map<String,String> settings=new LinkedHashMap<>();
            settings.put("AUDIT_JDBC_URL",url(name));settings.put("AUDIT_DB_USER","admin013_reader");settings.put("AUDIT_DB_PASSWORD",(String)environment(name).get("password"));
            settings.put("AUDIT_EXPECTED_DATABASE",(String)environment(name).get("database"));settings.put("AUDIT_EXPECTED_SCHEMA","public");
            try(Connection db=owner(name)){settings.put("AUDIT_EXPECTED_SERVER_ADDRESS",scalar(db,"SELECT INET_SERVER_ADDR()::text"));}
            settings.put("AUDIT_EXPECTED_SERVER_PORT","5432");settings.put("AUDIT_SSL_MODE","disable");settings.put("AUDIT_MANIFEST",directory.resolve("v27.tsv").toString());
            settings.put("AUDIT_S3_BUCKET",registry.get("run")+(name.equals("copy")?"-images-restored":"-images"));settings.put("AUDIT_S3_REGION","us-east-1");settings.put("AUDIT_S3_ENDPOINT","http://127.0.0.1:"+environment("storage").get("port"));
            try(AuditImageReader images=new AuditImageReader(settings);PrintWriter output=new PrintWriter(Files.newBufferedWriter(directory.resolve(name+"-audit.jsonl")))){
                int code=CatalogCompatibilityAudit.run(CatalogCompatibilityAudit.Settings.from(settings),ignored->reader(name),images::inspect,output);
                assertThat(code).as(name+" complete typed/object audit").isZero();
            }
            try(Connection db=owner(name)){assertThat(V27AuditDatabase.fingerprint(db)).isEqualTo(before);}
            System.out.println(name+" V27_READ_ONLY_AUDIT_COMPLETE");
        }
    }
}
