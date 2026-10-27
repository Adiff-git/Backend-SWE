package com.example.demo.media.dto.response;

import lombok.Value;
import lombok.ToString;

@Value
public class ImageAccessResponse {
    Long id;
    @ToString.Exclude String finalUrl;
}
