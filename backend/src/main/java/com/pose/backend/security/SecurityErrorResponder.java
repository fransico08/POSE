package com.pose.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;

@Component
public class SecurityErrorResponder {
    private final ObjectMapper mapper;

    public SecurityErrorResponder(ObjectMapper mapper) { this.mapper = mapper; }

    public void unauthorized(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String code = "AUTH_INVALID_TOKEN";
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof JwtValidationException validation
                    && validation.getErrors().stream().anyMatch(error -> error.getErrorCode().equals("AUTH_TOKEN_EXPIRED"))) {
                code = "AUTH_TOKEN_EXPIRED";
                break;
            }
        }
        write(request, response, 401, code, "Access token không hợp lệ hoặc đã hết hiệu lực.");
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      int status, String code, String message) throws IOException {
        ApiError error = ApiError.of(request, code, message, List.of());
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Trace-Id", error.traceId());
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        mapper.writeValue(response.getWriter(), error);
    }
}
