package com.pose.backend.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;

public record ApiError(String code, String message, List<FieldError> fieldErrors, String traceId) {
    public record FieldError(String field, String message) {}

    public static ApiError of(HttpServletRequest request, String code, String message, List<FieldError> fields) {
        String traceId = (String) request.getAttribute("pose.traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            request.setAttribute("pose.traceId", traceId);
        }
        return new ApiError(code, message, List.copyOf(fields), traceId);
    }
}
