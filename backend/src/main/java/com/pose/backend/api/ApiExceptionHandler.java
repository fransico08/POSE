package com.pose.backend.api;

import com.pose.backend.auth.AuthException;
import com.pose.backend.identity.AuthorizationDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiError> auth(AuthException exception, HttpServletRequest request) {
        return response(401, ApiError.of(request, exception.code(), exception.getMessage(), List.of()));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiError> authorization(AuthorizationDeniedException exception, HttpServletRequest request) {
        return response(403, ApiError.of(request, exception.code().name(),
                "Bạn không có quyền thao tác dữ liệu này.", List.of()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> authentication(HttpServletRequest request) {
        return response(401, ApiError.of(request, "AUTH_INVALID_TOKEN", "Phiên đăng nhập không hợp lệ.", List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiError.FieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage())).toList();
        return response(400, ApiError.of(request, "VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ.", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformedJson(HttpServletRequest request) {
        return response(400, ApiError.of(request, "VALIDATION_ERROR", "JSON gửi lên không hợp lệ.", List.of()));
    }

    private ResponseEntity<ApiError> response(int status, ApiError error) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .header("X-Trace-Id", error.traceId()).body(error);
    }
}
