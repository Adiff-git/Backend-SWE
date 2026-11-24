package com.example.demo.inventory.exception;

import com.example.demo.common.exception.ApplicationException;
import java.util.Map;

public final class AdminInventoryException extends ApplicationException {
    private final String code;
    private final Map<String, Object> properties;

    public AdminInventoryException(int status, String code, String detail) {
        this(status, code, detail, Map.of());
    }

    public AdminInventoryException(int status, String code, String detail, Map<String, Object> properties) {
        super(status, detail);
        this.code = "INVENTORY_" + code;
        this.properties = Map.copyOf(properties);
    }

    @Override public String getCode() { return code; }
    @Override public Map<String, Object> getProperties() { return properties; }
}
