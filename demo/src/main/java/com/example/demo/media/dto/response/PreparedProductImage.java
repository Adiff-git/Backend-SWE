package com.example.demo.media.dto.response;

import java.util.UUID;
import lombok.Value;

/** Internal owned COMPLETED upload proof. Never exposed as HTTP input/output. */
@Value
public class PreparedProductImage {
    UUID id;
    Long owner;
    Long version;
    String finalKey;
    String contentType;
}
