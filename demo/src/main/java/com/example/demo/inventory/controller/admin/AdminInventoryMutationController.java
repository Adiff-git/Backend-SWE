package com.example.demo.inventory.controller.admin;

import com.example.demo.inventory.dto.response.AdminInventoryOperation;
import com.example.demo.inventory.dto.request.AdminInventoryCommand.OperationType;
import com.example.demo.inventory.exception.AdminInventoryException;
import com.example.demo.inventory.service.AdminInventoryMutationService;
import com.example.demo.inventory.validator.AdminInventoryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inventory/variants")
public class AdminInventoryMutationController {
    private final AdminInventoryValidator validator;
    private final AdminInventoryMutationService mutations;

    @PostMapping("/{variantId}/replenishments")
    public AdminInventoryOperation replenish(@PathVariable String variantId, @RequestBody(required = false) byte[] body,
            @RequestHeader(name = "Content-Type", required = false) String contentType, JwtAuthenticationToken actor) {
        return apply(variantId, body, contentType, actor, OperationType.REPLENISHMENT);
    }

    @PostMapping("/{variantId}/corrections")
    public AdminInventoryOperation correct(@PathVariable String variantId, @RequestBody(required = false) byte[] body,
            @RequestHeader(name = "Content-Type", required = false) String contentType, JwtAuthenticationToken actor) {
        return apply(variantId, body, contentType, actor, OperationType.CORRECTION);
    }

    private AdminInventoryOperation apply(String variant, byte[] body, String contentType, JwtAuthenticationToken actor, OperationType type) {
        try {
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))) {
                throw unsupported();
            }
        } catch (org.springframework.http.InvalidMediaTypeException ignored) {
            throw unsupported();
        }
        if (body == null) throw AdminInventoryValidator.invalidRequest();
        return mutations.execute(Long.parseLong(actor.getName()), validator.command(variant, type, body));
    }

    private AdminInventoryException unsupported() {
        return new AdminInventoryException(415, "UNSUPPORTED_MEDIA_TYPE", "JSON media type is required");
    }
}
