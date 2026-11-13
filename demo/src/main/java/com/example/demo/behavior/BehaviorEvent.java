package com.example.demo.behavior;
import java.time.Instant;
import java.util.UUID;
public record BehaviorEvent(UUID id, BehaviorType eventType, UUID productId, String searchQuery,
    UUID clientEventId, SourceContext sourceContext, Instant occurredAt) { }
