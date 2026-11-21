package com.example.demo.review.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.ReviewIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
class ReviewControllerIT extends ReviewIntegrationSupport {
    @Autowired MockMvc mvc;
    @Test void update_shouldApplyOnlyOwnedExplicitFieldsAndKeepOneCommittedReview() throws Exception {
        String result = mvc.perform(post("/api/me/orders/" + purchase.order() + "/items/" + purchase.item() + "/reviews")
                .with(jwt().jwt(j -> j.subject(purchase.owner().toString())).authorities(new SimpleGrantedAuthority("MANAGE_ORDER")))
                .contentType("application/json").content("{\"rating\":1,\"comment\":\"original\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long review = JsonMapper.builder().build().readTree(result).get("id").asLong();
        mvc.perform(patch("/api/me/reviews/" + review)
                .with(jwt().jwt(j -> j.subject(purchase.foreign().toString())).authorities(new SimpleGrantedAuthority("MANAGE_ORDER")))
                .contentType("application/json").content("{\"rating\":5}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/me/reviews/" + review)
                .with(jwt().jwt(j -> j.subject(purchase.owner().toString())).authorities(new SimpleGrantedAuthority("MANAGE_ORDER")))
                .contentType("application/json").content("{\"rating\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(5)).andExpect(jsonPath("$.comment").value("original"));
    }
}
