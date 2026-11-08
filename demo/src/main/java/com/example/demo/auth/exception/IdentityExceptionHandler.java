package com.example.demo.auth.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages="com.example.demo.auth")
@Order(Ordered.HIGHEST_PRECEDENCE)
@lombok.RequiredArgsConstructor
public class IdentityExceptionHandler {
    private final com.example.demo.common.exception.GlobalExceptionHandler legacy;
    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<ProblemDetail> identity(IdentityException error) {
        ResponseEntity.BodyBuilder response=ResponseEntity.status(error.getStatus()).header("Cache-Control","no-store");
        if(error.getStatus()==401)response.header("WWW-Authenticate","Bearer");
        return response.body(problem(error));
    }
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(org.springframework.web.bind.MethodArgumentNotValidException failure,jakarta.servlet.http.HttpServletRequest request){
        if(!featurePath(request.getRequestURI()))return legacy.validation(failure,request);
        return identity(inputError(request));
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<?> malformed(Exception failure,jakarta.servlet.http.HttpServletRequest request){
        if(!featurePath(request.getRequestURI()))return legacy.invalid(failure,request);
        return identity(inputError(request));
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<?> unavailable(org.springframework.dao.DataAccessException failure,jakarta.servlet.http.HttpServletRequest request){
        if(!featurePath(request.getRequestURI()))return legacy.failure(failure,request);
        return identity(new IdentityException(503,"IDENTITY_UNAVAILABLE","Authentication service is temporarily unavailable"));
    }
    private boolean featurePath(String path){
        return path.endsWith("/api/auth/register") || path.contains("/api/auth/registration-verifications/") || path.contains("/api/auth/google-")
            || path.contains("/api/me/email-verification-challenges") || path.contains("/api/me/reauthentications") || path.endsWith("/api/me/password") || path.contains("/api/me/google-");
    }
    private IdentityException inputError(jakarta.servlet.http.HttpServletRequest request){
        boolean registration=request.getRequestURI().contains("registration-verifications");
        return new IdentityException(400,registration?"REGISTRATION_VERIFICATION_FAILED":"IDENTITY_INPUT_INVALID",registration?"Registration verification failed":"Invalid authentication input");
    }
    @ExceptionHandler(com.example.demo.common.exception.ApplicationException.class)
    public ResponseEntity<?> application(com.example.demo.common.exception.ApplicationException failure,jakarta.servlet.http.HttpServletRequest request){
        if(!featurePath(request.getRequestURI()))return legacy.failure(failure,request);
        String code=failure.getCode();
        if(code==null)code=switch(failure.getStatus()){
            case 400 -> "IDENTITY_INPUT_INVALID";
            case 401 -> "AUTHENTICATION_FAILED";
            case 403 -> "ACCOUNT_OPERATION_FORBIDDEN";
            case 404 -> "IDENTITY_NOT_FOUND";
            case 409 -> "IDENTITY_CONFLICT";
            default -> "IDENTITY_OPERATION_FAILED";
        };
        return identity(new IdentityException(failure.getStatus(),code,failure.getMessage()));
    }
    public static ProblemDetail problem(IdentityException error) {
        ProblemDetail problem=ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(error.getStatus()),error.getMessage());
        problem.setProperty("code",error.getCode());return problem;
    }
}
