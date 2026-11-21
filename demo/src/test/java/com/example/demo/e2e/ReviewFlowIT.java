package com.example.demo.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.fixture.review.ReviewFixtures;
import com.example.demo.media.dto.request.CreateImageUploadRequest;
import com.example.demo.media.dto.response.ImageUploadResponse;
import com.example.demo.media.integration.MediaInfrastructureTestSupport;
import com.example.demo.media.service.MediaService;
import com.example.demo.media.testdata.MediaTestData;
import com.example.demo.order.entity.OrderStatus;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest @AutoConfigureMockMvc
class ReviewFlowIT extends MediaInfrastructureTestSupport {
    @Autowired MediaService media;
    @Autowired MockMvc mvc;
    @Autowired EntityManager entities;
    @Autowired TransactionTemplate transactions;
    @Test void reviewFlow_shouldUploadCompleteCreateReadAndEditWithRealStorage() throws Exception {
        ReviewFixtures.Purchase purchase = transactions.execute(s -> ReviewFixtures.purchase(entities, OrderStatus.COMPLETED));
        CreateImageUploadRequest request = new CreateImageUploadRequest(); request.setContentType("image/png");
        ImageUploadResponse upload = media.createImageUpload(purchase.owner(), request);
        try (HttpClient browser = HttpClient.newHttpClient()) {
            HttpResponse<Void> put = browser.send(HttpRequest.newBuilder(URI.create(upload.getUploadUrl()))
                    .header("Content-Type", "image/png").PUT(HttpRequest.BodyPublishers.ofByteArray(MediaTestData.fixture("valid.png"))).build(), HttpResponse.BodyHandlers.discarding());
            assertThat(put.statusCode()).isBetween(200, 299);
            media.completeImageUpload(purchase.owner(), upload.getId());
            JsonMapper json = JsonMapper.builder().build();
            String created = mvc.perform(post("/api/me/orders/" + purchase.order() + "/items/" + purchase.item() + "/reviews")
                    .with(jwt().jwt(j -> j.subject(purchase.owner().toString())).authorities(new SimpleGrantedAuthority("MANAGE_ORDER")))
                    .contentType("application/json").content(json.writeValueAsString(Map.of("rating", 1, "imageIds", List.of(upload.getId().toString())))))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long review = json.readTree(created).get("id").asLong();
            mvc.perform(get("/api/products/" + purchase.product() + "/reviews"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].images[0].id").value(upload.getId().toString()));
            mvc.perform(patch("/api/me/reviews/" + review)
                    .with(jwt().jwt(j -> j.subject(purchase.owner().toString())).authorities(new SimpleGrantedAuthority("MANAGE_ORDER")))
                    .contentType("application/json").content("{\"rating\":5,\"imageIds\":[]}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.images").isEmpty());
            mvc.perform(get("/api/products/" + purchase.product() + "/rating-summary"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.reviewCount").value(1)).andExpect(jsonPath("$.averageRating").value(5));
            // Detaching never deletes the underlying completed upload or S3 object.
            String download = media.getImageDownload(purchase.owner(), upload.getId()).getFinalUrl();
            assertThat(browser.send(HttpRequest.newBuilder(URI.create(download)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
        }
    }
}
