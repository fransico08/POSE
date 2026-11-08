package com.pose.backend.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ApiRequestException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<ApiError.FieldError> fieldErrors;
    private final Map<String, Object> details;

    private ApiRequestException(int status, String code, String message, List<ApiError.FieldError> fields) {
        this(status, code, message, fields, null);
    }
    private ApiRequestException(int status, String code, String message, List<ApiError.FieldError> fields, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = List.copyOf(fields);
        this.details = details;
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
    public static ApiRequestException duplicate(String code, UUID visibleId) {
        return new ApiRequestException(409, code, "Thông tin liên hệ đã tồn tại.", List.of(),
                visibleId == null ? null : Map.of("duplicateId", visibleId));
    }
    public static ApiRequestException invalidOwner() {
        return unprocessable("ownerUserId", "Người phụ trách không đủ điều kiện.");
    }
    public static ApiRequestException unprocessable(String field, String message) {
        return new ApiRequestException(422, "VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ.",
                List.of(new ApiError.FieldError(field, message)));
    }
    public static ApiRequestException invalidLineOwner(int index) {
        return new ApiRequestException(422, "VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ.",
                List.of(new ApiError.FieldError("lines[" + index + "].offeringId", "Người phụ trách chưa được phân công sản phẩm/dịch vụ này.")));
    }
    public static ApiRequestException conversionOwner() {
        return new ApiRequestException(422, "VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ.",
                List.of(new ApiError.FieldError("ownerUserId", "Người phụ trách Lead không còn đủ điều kiện.")));
    }
    public static ApiRequestException conversionConflict(UUID visibleId) {
        return new ApiRequestException(409, "LEAD_CONVERSION_CONFLICT",
                "Thông tin liên hệ trùng với khách hàng khác. Cần kiểm tra hoặc bàn giao trước khi chuyển đổi.", List.of(),
                visibleId == null ? null : Map.of("duplicateId", visibleId));
    }
    public static ApiRequestException invalidContact(String field, String message) {
        return new ApiRequestException(400, "VALIDATION_ERROR", message, List.of(new ApiError.FieldError(field, message)));
    }

    public int status() { return status; }
    public String code() { return code; }
    public List<ApiError.FieldError> fieldErrors() { return fieldErrors; }
    public Map<String, Object> details() { return details; }
}
