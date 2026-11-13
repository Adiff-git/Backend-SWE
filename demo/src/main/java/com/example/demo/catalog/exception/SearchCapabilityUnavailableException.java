package com.example.demo.catalog.exception;
import com.example.demo.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;
public final class SearchCapabilityUnavailableException extends ApplicationException {
    public SearchCapabilityUnavailableException() { super(HttpStatus.SERVICE_UNAVAILABLE,"Fuzzy search capability is unavailable"); }
}
