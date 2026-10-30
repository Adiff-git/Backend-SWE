package com.example.demo.storage.dto.response;

import java.time.Instant;
import lombok.ToString;
import lombok.Value;

@Value
public class PreparedUpload {
    @ToString.Exclude String url;
    Instant expiresAt;
}
