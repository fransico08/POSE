package com.pose.backend.api;

import com.pose.backend.auth.AuthException;
import com.pose.backend.audit.AuditWriteException;
import com.pose.backend.identity.AuthorizationDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiRequestException.class)
    public ResponseEntity<ApiError> requestError(ApiRequestException exception, HttpServletRequest request) {
        return response(exception.status(), ApiError.of(request, exception.code(),
                exception.getMessage(), exception.fieldErrors()).withDetails(exception.details()));
    }

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

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidParameter(HttpServletRequest request) {
        return response(400, ApiError.of(request, "VALIDATION_ERROR", "Tham số không hợp lệ.", List.of()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataConflict(HttpServletRequest request) {
        return response(409, ApiError.of(request, "CONFLICT", "Dữ liệu xung đột với trạng thái hiện tại.", List.of()));
    }

    @ExceptionHandler(AuditWriteException.class)
    public ResponseEntity<ApiError> auditConflict(HttpServletRequest request) {
        return response(409, ApiError.of(request, "CONFLICT", "Không thể lưu thay đổi do xung đột dữ liệu.", List.of()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException exception, HttpServletRequest request) {
        return response(400, ApiError.of(request, "VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ.",
                List.of(new ApiError.FieldError(exception.getParameterName(), "Thiếu tham số bắt buộc."))));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMethod(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        ApiError error = ApiError.of(request, "METHOD_NOT_ALLOWED", "Phương thức không được hỗ trợ.", List.of());
        var builder = ResponseEntity.status(405).header("Cache-Control", "no-store").header("X-Trace-Id", error.traceId());
        if (exception.getSupportedHttpMethods() != null) builder.allow(exception.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new));
        return builder.body(error);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> notFound(HttpServletRequest request) {
        return response(404, ApiError.of(request, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên.", List.of()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMedia(HttpServletRequest request) {
        return response(415, ApiError.of(request, "UNSUPPORTED_MEDIA_TYPE", "Định dạng nội dung không được hỗ trợ.", List.of()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> accessDenied(HttpServletRequest request) {
        return response(403, ApiError.of(request, "FORBIDDEN_PERMISSION", "Bạn không có quyền thao tác dữ liệu này.", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        ApiError error = ApiError.of(request, "INTERNAL_ERROR", "Không thể xử lý yêu cầu. Vui lòng thử lại.", List.of());
        log.error("Unexpected API error traceId={} exceptionType={}", error.traceId(), exception.getClass().getName());
        return response(500, error);
    }

    private ResponseEntity<ApiError> response(int status, ApiError error) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .header("X-Trace-Id", error.traceId()).body(error);
    }
}
