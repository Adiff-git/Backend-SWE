package com.example.demo.media.dto.response;

import java.util.UUID;

import lombok.Value;
import lombok.ToString;

@Value
public class ImageAccessResponse {
    UUID id;
    @ToString.Exclude String finalUrl;
}
