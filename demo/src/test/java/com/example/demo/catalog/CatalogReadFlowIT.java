package com.example.demo.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.catalog.CatalogFixtures.ProductFixture;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogReadFlowIT {

	private static final String BUCKET = "catalog-private-images";
	private static final byte[] IMAGE_BYTES = "private product image bytes".getBytes(StandardCharsets.UTF_8);
	private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:4.12.0"))
			.withServices("s3")
			.withEnv("S3_SKIP_SIGNATURE_VALIDATION", "0");
	private static final String PREVIOUS_ACCESS_KEY = System.getProperty("aws.accessKeyId");
	private static final String PREVIOUS_SECRET_KEY = System.getProperty("aws.secretAccessKey");

	static {
		LOCALSTACK.start();
		System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
		System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
	}

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("catalog.images.bucket", () -> BUCKET);
		registry.add("catalog.images.region", LOCALSTACK::getRegion);
		registry.add("catalog.images.endpoint", () -> LOCALSTACK.getEndpoint().toString());
	}

	@AfterAll
	static void stopStorage() {
		restoreProperty("aws.accessKeyId", PREVIOUS_ACCESS_KEY);
		restoreProperty("aws.secretAccessKey", PREVIOUS_SECRET_KEY);
		LOCALSTACK.stop();
	}

	private static void restoreProperty(String name, String previous) {
		if (previous == null) {
			System.clearProperty(name);
		} else {
			System.setProperty(name, previous);
		}
	}

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper json;
	@Autowired private EntityManager entityManager;
	@Autowired private TransactionTemplate transactions;
	@Autowired private JdbcTemplate jdbc;

	@BeforeEach
	void prepare() {
		jdbc.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, products, categories CASCADE");
		try (S3Client client = s3Client()) {
			client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
		}
	}

	@AfterEach
	void cleanup() {
		jdbc.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, products, categories CASCADE");
	}

	@Test
	void anonymousListingThenDetailFetchesPrivateBytesOnlyThroughSignedReadUrls() throws Exception {
		StoredProduct published = persistProductWithImage(ProductStatus.PUBLISHED);
		StoredProduct draft = persistProductWithImage(ProductStatus.DRAFT);
		putPrivateObject(published.objectKey());
		putPrivateObject(draft.objectKey());

		JsonNode listing = json.readTree(mvc.perform(get("/api/products"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(listing.get("totalItems").asLong()).isEqualTo(1);
		assertThat(listing.get("items").get(0).get("id").asText()).isEqualTo(published.id().toString());
		String listUrl = listing.get("items").get(0).get("primaryImageUrl").asText();
		JsonNode detail = json.readTree(mvc.perform(get("/api/products/{id}", published.id()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(detail.get("images").size()).isEqualTo(1);
		assertThat(detail.get("images").get(0).get("isPrimary").asBoolean()).isTrue();
		String detailUrl = detail.get("images").get(0).get("url").asText();
		assertThat(detail.get("primaryImageUrl").asText()).isEqualTo(detailUrl);
		assertThat(listUrl).contains("X-Amz-Expires=300", "X-Amz-Signature=");
		assertThat(detailUrl).contains("X-Amz-Expires=300", "X-Amz-Signature=");
		assertThat(URI.create(detailUrl).getHost()).isIn("localhost", "127.0.0.1");

		try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
			HttpResponse<byte[]> signed = http.send(HttpRequest.newBuilder(URI.create(detailUrl))
					.timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
			assertThat(signed.statusCode()).isEqualTo(200);
			assertThat(signed.body()).isEqualTo(IMAGE_BYTES);
			// LocalStack validates presigned signatures but does not enforce IAM authorization by default.
			String tamperedUrl = detailUrl.substring(0, detailUrl.length() - 1)
					+ (detailUrl.endsWith("0") ? "1" : "0");
			HttpResponse<byte[]> tampered = http.send(HttpRequest.newBuilder(URI.create(tamperedUrl))
					.timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
			assertThat(tampered.statusCode()).isEqualTo(403);
		}
		mvc.perform(get("/api/products/{id}", draft.id())).andExpect(status().isNotFound());
		assertThat(listing.toString()).doesNotContain(draft.id().toString(), "objectKey", "stagingKey",
				"preparerSubject", "claimToken");
		assertThat(detail.toString()).doesNotContain("objectKey", "stagingKey", "preparerSubject", "claimToken");
	}

	private StoredProduct persistProductWithImage(ProductStatus status) {
		return transactions.execute(transaction -> {
			ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, status);
			ProductImage image = CatalogFixtures.image(fixture.product(), 0, true);
			entityManager.persist(image);
			entityManager.flush();
			return new StoredProduct(fixture.product().getId(), image.getObjectKey());
		});
	}

	private void putPrivateObject(String key) {
		try (S3Client client = s3Client()) {
			client.putObject(PutObjectRequest.builder().bucket(BUCKET).key(key)
					.acl(ObjectCannedACL.PRIVATE).contentType("image/png").build(), RequestBody.fromBytes(IMAGE_BYTES));
			assertThat(client.getObjectAcl(request -> request.bucket(BUCKET).key(key)).grants())
					.noneMatch(grant -> grant.grantee().uri() != null
							&& grant.grantee().uri().contains("AllUsers"));
		}
	}

	private S3Client s3Client() {
		return S3Client.builder()
				.endpointOverride(LOCALSTACK.getEndpoint())
				.region(Region.of(LOCALSTACK.getRegion()))
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
						LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
				.forcePathStyle(true)
				.build();
	}

	private record StoredProduct(UUID id, String objectKey) {
	}
}
