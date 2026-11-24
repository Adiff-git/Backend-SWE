package com.example.demo.inventory.dto.request;

import java.util.UUID;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class AdminInventoryCommand {
    public enum OperationType { REPLENISHMENT, CORRECTION }

    UUID operationId;
    UUID variantId;
    OperationType type;
    long delta;
    String reason;
}
