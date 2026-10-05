package com.pose.backend.api;

import java.util.List;

public final class ApiRequestException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<ApiError.FieldError> fieldErrors;

    private ApiRequestException(int status, String code, String message, List<ApiError.FieldError> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = List.copyOf(fields);
    }

    public static ApiRequestException validation(List<ApiError.FieldError> fields) {
        return new ApiRequestException(400, "VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ.", fields);
    }

    public static ApiRequestException notFound() {
        return new ApiRequestException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy dữ liệu.", List.of());
    }

    public static ApiRequestException conflict(String code) {
        return new ApiRequestException(409, code, "Dữ liệu xung đột với trạng thái hiện tại.", List.of());
    }

    public int status() { return status; }
    public String code() { return code; }
    public List<ApiError.FieldError> fieldErrors() { return fieldErrors; }
}
