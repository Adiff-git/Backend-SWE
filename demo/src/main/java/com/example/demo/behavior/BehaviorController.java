package com.example.demo.behavior;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import com.example.demo.common.identity.CurrentUserIdProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name="bearerAuth")
@io.swagger.v3.oas.annotations.security.SecurityScheme(name="bearerAuth", type=io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP, scheme="bearer", bearerFormat="JWT")
public class BehaviorController {
    private final BehaviorService service;
    private final CurrentUserIdProvider identity;
    @PostMapping("/api/me/behavior/events")
    @Operation(summary="Record an intentional Customer interaction", description="PRODUCT_VIEW, SEARCH or PRODUCT_CLICK only. clientEventId is required. Identical retries return 200; new events 201; conflicting reuse 409. Server supplies owner and timestamp. Unknown fields are rejected.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201", description="New event", content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=EventReceipt.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="Identical retry", content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=EventReceipt.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400", description="Invalid payload"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401", description="Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403", description="Customer role required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404", description="Product unavailable"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409", description="Conflicting clientEventId")
    })
    public ResponseEntity<EventReceipt> ingest(@RequestBody BehaviorRequest event, @RequestParam org.springframework.util.MultiValueMap<String,String> parameters) {
        parameters(parameters, java.util.Set.of());
        boolean created = service.ingest(identity.getCurrentUserId(), event);
        return ResponseEntity.status(created ? 201 : 200).header("Cache-Control", "no-store").body(new EventReceipt(created));
    }
    @GetMapping("/api/me/behavior")
    @Operation(summary="Read current Customer history", description="Newest first by occurredAt and id. size 1..100; page 0..100000. Inclusive from/to UTC instants; range <=90 days; expired events excluded.")
    public ResponseEntity<BehaviorPage> history(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
        @RequestParam(required=false) BehaviorType eventType, @RequestParam(required=false) Instant from, @RequestParam(required=false) Instant to, @RequestParam org.springframework.util.MultiValueMap<String,String> parameters) {
        parameters(parameters, java.util.Set.of("page", "size", "eventType", "from", "to"));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.history(identity.getCurrentUserId(), eventType, from, to, page, size));
    }
    @GetMapping("/api/admin/behavior/overview")
    @Operation(summary="Administrator aggregate behavior metrics", description="days: 7,30,90. Top ten products and normalized searches. Search terms require at least three distinct Customers.")
    public ResponseEntity<BehaviorOverview> overview(@RequestParam(defaultValue="7") int days, @RequestParam org.springframework.util.MultiValueMap<String,String> parameters) {
        parameters(parameters, java.util.Set.of("days"));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.overview(days));
    }
    private void parameters(org.springframework.util.MultiValueMap<String,String> parameters, java.util.Set<String> allowed) {
        parameters.forEach((key, values) -> {
            if (!allowed.contains(key) || values.size() != 1) throw new BehaviorException(400, "Unsupported or duplicate parameter");
        });
    }
}
