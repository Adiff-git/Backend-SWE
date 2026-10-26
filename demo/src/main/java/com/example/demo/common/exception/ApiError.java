package com.example.demo.common.exception;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.List;
public final class ApiError extends ProblemDetail {
    public ApiError(int status,String message,String path,Map<String,List<String>> errors) {
        super(status);
        setType(URI.create("about:blank"));
        setTitle(HttpStatus.valueOf(status).getReasonPhrase());
        setDetail(message);
        setInstance(URI.create(path));
        setProperty("timestamp",Instant.now().toString());
        setProperty("message",message);
        setProperty("path",path);
        setProperty("fieldErrors",errors);
    }
}
