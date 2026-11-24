package com.example.demo.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import com.example.demo.inventory.dto.request.AdminInventoryCommand.OperationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AdminInventoryOperation {
    UUID operationId;
    UUID variantId;
    String adminId;
    OperationType operationType;
    String delta;
    String onHandBefore;
    String onHandAfter;
    String reservedObserved;
    String availableAfter;
    String reason;
    Instant occurredAt;
}
