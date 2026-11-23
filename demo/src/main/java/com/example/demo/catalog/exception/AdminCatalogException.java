package com.example.demo.catalog.exception;

import com.example.demo.common.exception.ApplicationException;
import java.util.Map;

/** Safe domain failures; callers provide fixed public details, never driver/storage messages. */
public final class AdminCatalogException extends ApplicationException {
    private final String code;
    private final Map<String, Object> properties;

    public AdminCatalogException(int status, String code, String detail) {
        this(status, code, detail, Map.of());
    }

    public AdminCatalogException(int status, String code, String detail, Map<String, Object> properties) {
        super(status, detail);
        this.code = code;
        this.properties = Map.copyOf(properties);
    }

    @Override public String getCode() { return code; }
    @Override public Map<String, Object> getProperties() { return properties; }
}
