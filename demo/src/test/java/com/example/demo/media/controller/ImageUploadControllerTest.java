package com.example.demo.media.controller;

import com.example.demo.media.dto.response.ImageUploadResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImageUploadControllerTest extends MediaControllerTestSupport {
    @Test void create_shouldReturnCreatedWithoutProfilePermission() throws Exception {
        when(media.createImageUpload(eq(8L), any())).thenReturn(new ImageUploadResponse(1L, "capability", Instant.now().plusSeconds(900)));
        mvc.perform(post("/api/image-uploads").with(jwt()).contentType("application/json").content("{\"contentType\":\"image/png\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("id").value(1)).andExpect(jsonPath("uploadUrl").value("capability"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"contentType\":null}", "{\"contentType\":\"text/plain\"}",
            "{\"contentType\":\"image/png\",\"ownerUserId\":99}"})
    void create_shouldRejectInvalidRequest(String body) throws Exception {
        mvc.perform(post("/api/image-uploads").with(jwt()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
    @Test void create_shouldRejectMissingAuthentication() throws Exception {
        mvc.perform(post("/api/image-uploads").contentType("application/json").content("{\"contentType\":\"image/png\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void download_shouldRejectMalformedIdAndUnmatchedRoutes() throws Exception {
        mvc.perform(get("/api/image-uploads/not-a-number/download-url").with(jwt())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/image-uploads/1/private").with(jwt())).andExpect(status().isForbidden());
    }
}
