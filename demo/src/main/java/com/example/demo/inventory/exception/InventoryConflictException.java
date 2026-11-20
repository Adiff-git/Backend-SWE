package com.example.demo.inventory.exception;

import com.example.demo.common.exception.ApplicationException;

public final class InventoryConflictException extends ApplicationException {
    public InventoryConflictException() { super(409, "Inventory state conflict"); }
    @Override public String getCode() { return "INVENTORY_CONFLICT"; }
}
