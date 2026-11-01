package com.example.demo.wishlist.controller;

import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.wishlist.dto.response.WishlistItemResponse;
import com.example.demo.wishlist.dto.response.WishlistPage;
import com.example.demo.wishlist.validator.WishlistValidator;
import com.example.demo.wishlist.exception.InvalidWishlistRequestException;
import com.example.demo.wishlist.service.AddWishlistResult;
import com.example.demo.wishlist.service.WishlistService;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/wishlist")
@RequiredArgsConstructor
public class WishlistController {
    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private final WishlistService service;
    private final CurrentUserIdProvider identity;

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeProduct(@PathVariable String productId,
            @RequestParam MultiValueMap<String, String> parameters, @RequestBody(required = false) byte[] body) {
        validateMutation(parameters, body);
        UUID id = parseProductId(productId);
        service.removeProduct(identity.getCurrentUserId(), id);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @GetMapping
    public ResponseEntity<WishlistPage> listWishlist(@RequestParam MultiValueMap<String, String> parameters) {
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            if ((!"page".equals(entry.getKey()) && !"size".equals(entry.getKey())) || entry.getValue().size() != 1) {
                throw new InvalidWishlistRequestException("Invalid or duplicate wishlist query parameter");
            }
        }
        int page = parseNumber(parameters.getFirst("page"), WishlistValidator.DEFAULT_PAGE);
        int size = parseNumber(parameters.getFirst("size"), WishlistValidator.DEFAULT_SIZE);
        if (size < 1 || size > WishlistValidator.MAX_SIZE) {
            throw new InvalidWishlistRequestException("Invalid wishlist page size");
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(service.listWishlist(identity.getCurrentUserId(), page, size));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<WishlistItemResponse> addProduct(@PathVariable String productId,
            @RequestParam MultiValueMap<String, String> parameters, @RequestBody(required = false) byte[] body) {
        validateMutation(parameters, body);
        UUID id = parseProductId(productId);
        AddWishlistResult result = service.addProduct(identity.getCurrentUserId(), id);
        return ResponseEntity.status(result.isCreated() ? 201 : 200).header("Cache-Control", "no-store")
                .body(result.getItem());
    }

    private void validateMutation(MultiValueMap<String, String> parameters, byte[] body) {
        if (!parameters.isEmpty() || (body != null && body.length > 0)) {
            throw new InvalidWishlistRequestException("Wishlist mutations accept no query parameters or body");
        }
    }

    private UUID parseProductId(String value) {
        if (!CANONICAL_UUID.matcher(value).matches()) {
            throw new InvalidWishlistRequestException("Invalid product identifier");
        }
        return UUID.fromString(value);
    }

    private int parseNumber(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (!value.matches("0|[1-9][0-9]*")) {
            throw new InvalidWishlistRequestException("Invalid wishlist pagination");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new InvalidWishlistRequestException("Invalid wishlist pagination");
        }
    }
}
