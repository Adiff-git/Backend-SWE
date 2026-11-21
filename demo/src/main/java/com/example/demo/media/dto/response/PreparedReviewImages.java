package com.example.demo.media.dto.response;

import java.util.Map;
import java.util.UUID;
import lombok.Value;

/** Internal immutable metadata proof, never serialized as a public response. */
@Value
public class PreparedReviewImages {
    Map<UUID, String> finalKeys;
    public PreparedReviewImages(Map<UUID, String> keys) { this.finalKeys = Map.copyOf(keys); }
}
