package com.pose.backend.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiError(String code, String message, List<FieldError> fieldErrors, String traceId,
                       @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> details) {
    public record FieldError(String field, String message) {}

    public static ApiError of(HttpServletRequest request, String code, String message, List<FieldError> fields) {
        String traceId = (String) request.getAttribute("pose.traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            request.setAttribute("pose.traceId", traceId);
        }
        return new ApiError(code, message, List.copyOf(fields), traceId, null);
    }
    public ApiError withDetails(Map<String, Object> value) { return new ApiError(code, message, fieldErrors, traceId, value); }
}
