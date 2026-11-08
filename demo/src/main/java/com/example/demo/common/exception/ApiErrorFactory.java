package com.example.demo.common.exception;
import java.util.List;
import java.util.Map;
import java.sql.SQLException;
import org.springframework.stereotype.Component;
@Component
public class ApiErrorFactory {
    public ApiError create(int status,String message,String path,Map<String,List<String>> fields) {
        return new ApiError(status,message,path,fields);
    }
    public ApiError from(Throwable failure,String path) {
        if(failure instanceof ApplicationException app) {
            ApiError error=create(app.getStatus(),app.getMessage(),path,Map.of());if(app.getCode()!=null)error.setProperty("code",app.getCode());return error;
        }
        if(failure instanceof org.springframework.dao.OptimisticLockingFailureException
                || failure instanceof jakarta.persistence.OptimisticLockException)
            return create(409,"Concurrent update; you may retry",path,Map.of());
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                    && "23503".equals(constraint.getSQLState())) {
                if ("fk_wishlist_items_user_id".equals(constraint.getConstraintName()))
                    return create(401,"Authenticated identity is unavailable",path,Map.of());
                if ("fk_wishlist_items_product_id".equals(constraint.getConstraintName()))
                    return create(404,"Product not found",path,Map.of());
            }
            if(cause instanceof SQLException sql) {
                if("55P03".equals(sql.getSQLState())) return create(409,"Concurrent update; you may retry",path,Map.of());
                if("23505".equals(sql.getSQLState()) && sql.getMessage()!=null && (sql.getMessage().contains("uk_users_username_key") || sql.getMessage().contains("uk_users_email_key"))) return create(409,"Username or email is already in use",path,Map.of());
            }
        }
        return create(500,"An unexpected error occurred",path,Map.of());
    }
}
