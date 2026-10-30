package com.example.demo.media.dto.request;

import com.example.demo.media.exception.InvalidImageRequestException;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

@Getter
public class CreateImageUploadRequest {
    @NotNull @Pattern(regexp = "image/(jpeg|png|webp)") private String contentType;

    public void setContentType(String contentType) { this.contentType = contentType; }
    @JsonAnySetter public void rejectUnknown(String name, JsonNode value) { throw new InvalidImageRequestException(); }
}
