package com.example.demo.common.exception;
import java.util.List;
import java.util.Map;
import java.sql.SQLException;
import org.springframework.stereotype.Component;
@Component
public class ApiErrorFactory {
    private static final java.util.Set<String> INVENTORY_CONSTRAINTS = java.util.Set.of(
            "uk_inventory_balances_original_variant_id", "uk_inventory_balances_product_variant_id",
            "uk_inventory_balances_id_original_variant_id", "fk_inventory_balances_product_variant_id",
            "ck_inventory_balances_quantities", "ck_inventory_balances_variant_identity",
            "fk_inventory_reservations_order_id", "fk_inventory_reservations_order_item_id_order_id",
            "fk_inventory_reservations_balance_variant", "uk_inventory_reservations_order_item_id",
            "uk_inventory_reservations_order_id_original_variant_id", "ck_inventory_reservations_quantity",
            "ck_inventory_reservations_state", "ck_inventory_reservations_times",
            "ct_inventory_reservations_immutable", "ct_inventory_balance_retention",
            "ct_orders_inventory_participation", "ct_orders_inventory_integrity",
            "ct_order_items_inventory_integrity", "ct_inventory_reservations_order_integrity",
            "ct_inventory_balances_allocation_integrity", "ct_inventory_reservations_allocation_integrity");
    private static final java.util.Set<String> VOUCHER_CONSTRAINTS = java.util.Set.of(
            "ck_vouchers_definition", "ck_vouchers_usage", "ck_voucher_usages_count",
            "ck_voucher_entitlements_state", "ck_voucher_redemptions_snapshot",
            "ct_voucher_entitlements_immutable", "ct_voucher_redemptions_immutable",
            "ct_voucher_usage_integrity", "ct_orders_voucher_integrity", "ck_orders_voucher_totals",
            "ck_carts_voucher_revision", "fk_carts_owned_voucher", "fk_voucher_entitlements_usage",
            "fk_voucher_redemptions_owned_order", "fk_voucher_redemptions_grant",
            "uk_voucher_redemptions_order_id", "uk_voucher_redemptions_voucher_entitlement_id");
    public ApiError create(int status,String message,String path,Map<String,List<String>> fields) {
        return new ApiError(status,message,path,fields);
    }
    public ApiError from(Throwable failure,String path) {
        // The existing global cart advice delegates other domains' media errors here.
        if (failure instanceof org.springframework.web.HttpMediaTypeNotSupportedException
                && path != null && (path.equals("/api/me/orders") || path.startsWith("/api/me/orders/")))
            return create(415,"Unsupported request media type",path,Map.of());
        if(failure instanceof ApplicationException app) {
            ApiError error=create(app.getStatus(),app.getMessage(),path,Map.of());
            if(app.getCode()!=null)error.setProperty("code",app.getCode());
            app.getProperties().forEach(error::setProperty);
            if ("/api/me/cart/checkout".equals(path) && app.getStatus()==409
                    && !"VOUCHER_NOT_APPLICABLE".equals(app.getCode())
                    && (app.getCode()==null || !app.getCode().startsWith("CHECKOUT_")))
                error.setProperty("code","CHECKOUT_CONFLICT");
            return error;
        }
        if(failure instanceof org.springframework.dao.OptimisticLockingFailureException
                || failure instanceof jakarta.persistence.OptimisticLockException)
            return conflict(path);
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                    && "23505".equals(constraint.getSQLState())
                    && "uk_product_reviews_order_item_id".equals(constraint.getConstraintName())) {
                return create(409, "Review conflicts with the current state", path, Map.of());
            }
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                    && constraint.getConstraintName() != null
                    && VOUCHER_CONSTRAINTS.contains(constraint.getConstraintName())
                    && isIntegrityState(constraint.getSQLState())) {
                return from(new com.example.demo.voucher.exception.VoucherConflictException(), path);
            }
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                    && constraint.getConstraintName() != null
                    && INVENTORY_CONSTRAINTS.contains(constraint.getConstraintName())
                    && isIntegrityState(constraint.getSQLState())) {
                return from(new com.example.demo.inventory.exception.InventoryConflictException(), path);
            }
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                    && "23503".equals(constraint.getSQLState())) {
                if ("fk_carts_user_id".equals(constraint.getConstraintName()))
                    return create(401,"Authenticated identity is unavailable",path,Map.of());
                if ("fk_cart_items_product_id".equals(constraint.getConstraintName())
                        || "fk_cart_items_product_variant_id".equals(constraint.getConstraintName()))
                    return create(404,"Product variant not found",path,Map.of());
                if ("fk_wishlist_items_user_id".equals(constraint.getConstraintName()))
                    return create(401,"Authenticated identity is unavailable",path,Map.of());
                if ("fk_wishlist_items_product_id".equals(constraint.getConstraintName()))
                    return create(404,"Product not found",path,Map.of());
            }
            if(cause instanceof SQLException sql) {
                String inventoryConstraint = postgresConstraint(sql);
                if (inventoryConstraint != null && VOUCHER_CONSTRAINTS.contains(inventoryConstraint)
                        && isIntegrityState(sql.getSQLState())) {
                    return from(new com.example.demo.voucher.exception.VoucherConflictException(), path);
                }
                if (inventoryConstraint != null && INVENTORY_CONSTRAINTS.contains(inventoryConstraint)
                        && isIntegrityState(sql.getSQLState())) {
                    return from(new com.example.demo.inventory.exception.InventoryConflictException(), path);
                }
                if ("/api/me/cart/checkout".equals(path) && "23505".equals(sql.getSQLState())
                        && sql.getMessage()!=null && (sql.getMessage().contains("uk_checkout_receipts_customer_id_operation_idempotency_key")
                        || sql.getMessage().contains("uk_checkout_receipts_order_id") || sql.getMessage().contains("uk_orders_order_number")))
                    return conflict(path);
                if("55P03".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState())) return conflict(path);
                if("23505".equals(sql.getSQLState()) && sql.getMessage()!=null
                        && (sql.getMessage().contains("uk_carts_user_id") || sql.getMessage().contains("uk_cart_items_cart_id_product_variant_id")))
                    return conflict(path);
                if("23505".equals(sql.getSQLState()) && sql.getMessage()!=null && (sql.getMessage().contains("uk_users_username_key") || sql.getMessage().contains("uk_users_email_key"))) return create(409,"Username or email is already in use",path,Map.of());
            }
        }
        return create(500,"An unexpected error occurred",path,Map.of());
    }
    private boolean isIntegrityState(String state) {
        return "23503".equals(state) || "23505".equals(state) || "23514".equals(state);
    }

    /** The existing PostgreSQL driver is runtime-scoped; read its public structured fields without a new compile dependency. */
    String postgresConstraint(SQLException sql) {
        if (!"org.postgresql.util.PSQLException".equals(sql.getClass().getName())) return null;
        try {
            Object serverError = sql.getClass().getMethod("getServerErrorMessage").invoke(sql);
            if (serverError == null) return null;
            Object constraint = serverError.getClass().getMethod("getConstraint").invoke(serverError);
            return constraint instanceof String name ? name : null;
        } catch (ReflectiveOperationException unavailable) {
            return null; // Unknown driver shape keeps the established safe500; never parse user-controlled SQL text.
        }
    }
    private ApiError conflict(String path) {
        ApiError error=create(409,"Concurrent update; you may retry",path,Map.of());
        if ("/api/me/cart/checkout".equals(path)) error.setProperty("code","CHECKOUT_CONFLICT");
        else if(path!=null && (path.equals("/api/me/cart") || path.startsWith("/api/me/cart/")))
            error.setProperty("code","CART_CONFLICT");
        return error;
    }
}
