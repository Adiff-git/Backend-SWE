package com.example.demo.media.integration;

import com.example.demo.media.testdata.MediaTestData;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Starts the actual executable JAR, so decoder discovery is outside the test classpath. */
@SpringBootTest
class PackagedImageFlowIT extends MediaInfrastructureTestSupport {
    @Autowired DataSource dataSource;
    @Autowired JsonMapper json;

    @Test void packagedJar_shouldUploadDecodeAndViewAllFormatsOnJava25() throws Exception {
        int port;
        try (ServerSocket reserved = new ServerSocket(0)) { port = reserved.getLocalPort(); }
        String databaseUrl;
        String databaseUser;
        try (Connection connection = dataSource.getConnection()) {
            databaseUrl = connection.getMetaData().getURL();
            databaseUser = connection.getMetaData().getUserName();
        }
        Path jar = Path.of("target", "demo-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        assertThat(Files.isRegularFile(jar)).isTrue();
        String javaName = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", javaName).toString(),
                "-jar", jar.toString(), "--server.port=" + port, "--spring.profiles.active=local",
                "--spring.datasource.url=" + databaseUrl, "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.datasource.username=" + databaseUser, "--spring.datasource.password=test",
                "--storage.s3.endpoint=" + STORAGE.getEndpoint(), "--storage.s3.signing-endpoint=" + STORAGE.getEndpoint(),
                "--storage.s3.bucket=" + bucket, "--security.auth.cleanup.enabled=false"));
        Path output = Files.createTempFile(Path.of("target"), "packaged-image-", ".log");
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("JWT_SECRET", "controlled-packaged-test-secret-at-least-32-bytes");
        builder.environment().put("JWT_ISSUER", "urn:packaged:test");
        builder.environment().put("CORS_ALLOWED_ORIGINS", "http://localhost:3000");
        Process application = builder.start();
        URI base = URI.create("http://localhost:" + port);
        try (HttpClient browser = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            boolean ready = false;
            long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            while (application.isAlive() && System.nanoTime() < deadline) {
                try {
                    ready = browser.send(HttpRequest.newBuilder(base.resolve("/api/me")).timeout(Duration.ofSeconds(2)).GET().build(),
                            HttpResponse.BodyHandlers.discarding()).statusCode() == 401;
                    if (ready) break;
                } catch (java.io.IOException failure) { /* Child JVM is still starting. */ }
                Thread.sleep(200);
            }
            assertThat(ready).as("Packaged app startup; diagnostic file: %s", output).isTrue();
            HttpResponse<String> registration = browser.send(HttpRequest.newBuilder(base.resolve("/api/auth/register"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                            "{\"username\":\"PackagedOwner\",\"password\":\"Password 123\"}")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(registration.statusCode()).isEqualTo(201);
            String token = registration.headers().firstValue("Authorization").orElseThrow();
            for (String fixture : List.of("valid.jpg", "valid.png", "lossy.webp")) {
                String type = fixture.endsWith("jpg") ? "image/jpeg" : fixture.endsWith("png") ? "image/png" : "image/webp";
                HttpResponse<String> prepare = browser.send(HttpRequest.newBuilder(base.resolve("/api/image-uploads"))
                        .header("Authorization", token).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"contentType\":\"" + type + "\"}")).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(prepare.statusCode()).isEqualTo(201);
                JsonNode upload = json.readTree(prepare.body());
                byte[] bytes = MediaTestData.fixture(fixture);
                assertThat(browser.send(HttpRequest.newBuilder(URI.create(upload.get("uploadUrl").asString()))
                        .header("Content-Type", type).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
                HttpResponse<String> complete = browser.send(HttpRequest.newBuilder(base.resolve("/api/image-uploads/"
                        + upload.get("id").asString() + "/complete")).header("Authorization", token)
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(complete.statusCode()).isEqualTo(200);
                URI download = URI.create(json.readTree(complete.body()).get("finalUrl").asString());
                HttpResponse<byte[]> image = browser.send(HttpRequest.newBuilder(download).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                assertThat(image.statusCode()).isEqualTo(200);
                assertThat(image.body()).isEqualTo(bytes);
            }
            assertThat(Files.readString(output)).doesNotContain(token);
        } finally {
            application.destroy();
            if (!application.waitFor(10, TimeUnit.SECONDS)) application.destroyForcibly().waitFor();
        }
    }
}
