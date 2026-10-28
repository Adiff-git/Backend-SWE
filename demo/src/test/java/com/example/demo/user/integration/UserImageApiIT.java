package com.example.demo.user.integration;

import com.example.demo.media.dto.response.ImageUploadResponse;
import com.example.demo.media.integration.SeededMediaTestSupport;
import com.example.demo.storage.exception.StorageUnavailableException;
import com.example.demo.storage.service.ObjectStorageService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserImageApiIT extends SeededMediaTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean ObjectStorageService storage;

    private String register(String name) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", name, "password", "Password 123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Authorization");
    }
    private Long id(String name) { return users.findByUsernameIdentifier(name).orElseThrow().getId(); }
    private Long completed(Long actor) {
        ImageUploadResponse upload = prepare(actor);
        stage(upload);
        return media.completeImageUpload(actor, upload.getId()).getId();
    }
    private String select(Long image) { return "{\"profileImageId\":" + image + "}"; }

    @Test void update_shouldPreserveClearAndReplaceWithoutDeletingObjects() throws Exception {
        String token = register("Owner");
        Long actor = id("Owner");
        Long first = completed(actor);
        mvc.perform(put("/api/me").header("Authorization", token).contentType("application/json").content(select(first)))
                .andExpect(status().isOk()).andExpect(jsonPath("profileImageId").value(first))
                .andExpect(jsonPath("profileImageUrl").isString()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(put("/api/me").header("Authorization", token).contentType("application/json").content("{\"displayName\":\"Updated\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("profileImageId").value(first));
        Long second = completed(actor);
        mvc.perform(put("/api/me").header("Authorization", token).contentType("application/json").content(select(second)))
                .andExpect(status().isOk()).andExpect(jsonPath("profileImageId").value(second));
        mvc.perform(put("/api/me").header("Authorization", token).contentType("application/json").content("{\"profileImageId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("profileImageId").isEmpty()).andExpect(jsonPath("profileImageUrl").isEmpty());
        assertThat(s3.listObjectsV2(request -> request.bucket(bucket).prefix("images/")).contents()).hasSize(2);
    }

    @Test void profile_shouldPermitAttachedViewingButDenyForeignRenewalAndAttachment() throws Exception {
        String ownerToken = register("Owner");
        Long owner = id("Owner");
        Long image = completed(owner);
        mvc.perform(put("/api/me").header("Authorization", ownerToken).contentType("application/json").content(select(image))).andExpect(status().isOk());
        String viewer = register("Viewer");
        mvc.perform(get("/api/users/" + owner).header("Authorization", viewer)).andExpect(status().isOk())
                .andExpect(jsonPath("profileImageUrl").isString()).andExpect(jsonPath("email").doesNotExist());
        mvc.perform(get("/api/image-uploads/" + image + "/download-url").header("Authorization", viewer)).andExpect(status().isNotFound());
        mvc.perform(put("/api/me").header("Authorization", viewer).contentType("application/json")
                .content("{\"displayName\":\"MustRollback\",\"profileImageId\":" + image + "}"))
                .andExpect(status().isNotFound());
        assertThat(users.findById(id("Viewer")).orElseThrow().getDisplayName()).isNull();
    }

    @Test void update_shouldRollbackAllFields_whenImagePendingOrSigningUnavailable() throws Exception {
        String token = register("Owner");
        Long actor = id("Owner");
        ImageUploadResponse pending = prepare(actor);
        mvc.perform(put("/api/me").header("Authorization", token).contentType("application/json")
                .content("{\"displayName\":\"Invalid\",\"profileImageId\":" + pending.getId() + "}"))
                .andExpect(status().isConflict());
        Long ready = completed(actor);
        doThrow(new StorageUnavailableException()).when(storage).prepareDownload(any(), any());
        mvc.perform(put("/api/me").header("Authorization", token).contentType("application/json")
                .content("{\"displayName\":\"Invalid\",\"profileImageId\":" + ready + "}"))
                .andExpect(status().isServiceUnavailable());
        assertThat(users.findById(actor).orElseThrow().getDisplayName()).isNull();
        assertThat(users.findById(actor).orElseThrow().getProfileImageId()).isNull();
    }

    @Test void media_shouldRequireNoProfilePermissionWhileProfileStillRequiresIt() throws Exception {
        String token = register("Owner");
        Long actor = id("Owner");
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", actor);
        mvc.perform(post("/api/image-uploads").header("Authorization", token).contentType("application/json")
                .content("{\"contentType\":\"image/png\"}")).andExpect(status().isCreated());
        mvc.perform(put("/api/me").header("Authorization", token).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
}
