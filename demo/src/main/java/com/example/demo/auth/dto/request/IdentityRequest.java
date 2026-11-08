package com.example.demo.auth.dto.request;

import com.example.demo.auth.exception.IdentityException;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.JsonNode;

public abstract class IdentityRequest {
    protected String text(JsonNode value) {
        if (value==null || value.isNull()) return null;
        if (!value.isString()) throw new IdentityException(400,"INVALID_REQUEST","Invalid request field type");
        return value.asString();
    }
    @JsonAnySetter
    public void rejectUnknown(String property,JsonNode value) {
        throw new IdentityException(400,"INVALID_REQUEST","Unknown request field");
    }
}
