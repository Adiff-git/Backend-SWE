package com.example.demo.auth.converter;

import com.example.demo.auth.config.IdentityProperties;
import com.example.demo.auth.dto.response.GoogleAttemptResponse;
import com.example.demo.auth.service.GoogleAttemptStart;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleAttemptConverter {
    private final IdentityProperties properties;
    public GoogleAttemptResponse toResponse(GoogleAttemptStart start) {
        String callback=properties.getCallbackUri().toString();
        String suffix="/login/oauth2/code/google";
        String base=callback.substring(0,callback.length()-suffix.length());
        return new GoogleAttemptResponse(base+"/oauth2/authorization/google?attempt="+start.getTicket(),start.getExpiresAt());
    }
}
