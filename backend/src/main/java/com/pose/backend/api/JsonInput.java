package com.pose.backend.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public final class JsonInput {
    private final JsonNode body;
    private JsonInput(JsonNode body) { this.body = body; }
    public static JsonInput of(JsonNode body, String... allowed) {
        if (body == null || !body.isObject()) throw invalid("body", "Phải là JSON object.");
        Set<String> fields = Set.of(allowed);
        body.fieldNames().forEachRemaining(f -> { if (!fields.contains(f)) throw invalid(f, "Trường không được phép."); });
        return new JsonInput(body);
    }
    public boolean has(String field) { return body.has(field); }
    public boolean missingOrBlank(String field) {
        JsonNode value=body.get(field);
        return value==null || value.isNull() || value.isTextual() && value.asText().isBlank();
    }
    public void requireAny() { if (body.isEmpty()) throw invalid("body", "Cần ít nhất một trường."); }
    public String text(String field, int max, boolean required, boolean nullable) {
        JsonNode value = body.get(field);
        if (value == null) {
            if (required) throw invalid(field, "Thiếu trường bắt buộc.");
            return null;
        }
        if (nullable && value.isNull()) return null;
        if (!value.isTextual()) throw invalid(field, "Phải là chuỗi.");
        String result = value.asText().strip();
        if (result.isBlank() || result.length() > max) throw invalid(field, "Không được trống; dài tối đa " + max + " ký tự.");
        return result;
    }
    public UUID uuid(String field) {
        String value = text(field, 36, true, false);
        try {
            UUID result = UUID.fromString(value);
            if (!result.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return result;
        } catch (IllegalArgumentException ex) { throw invalid(field, "UUID không hợp lệ."); }
    }
    public List<UUID> uuids(String field, boolean required) {
        JsonNode values = body.get(field);
        if (values == null && !required) return List.of();
        if (values == null || !values.isArray() || values.size() > 100) throw invalid(field, "Phải là mảng tối đa 100 UUID.");
        List<UUID> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual()) throw invalid(field, "UUID không hợp lệ.");
            try {
                UUID id = UUID.fromString(value.asText());
                if (!id.toString().equalsIgnoreCase(value.asText())) throw new IllegalArgumentException();
                result.add(id);
            } catch (IllegalArgumentException ex) { throw invalid(field, "UUID không hợp lệ."); }
        }
        if (new HashSet<>(result).size() != result.size()) throw invalid(field, "Không được có ID trùng.");
        return List.copyOf(result);
    }
    public List<String> strings(String field, boolean emptyAllowed) {
        JsonNode values = body.get(field);
        if (values == null || !values.isArray() || values.size() > 100 || (!emptyAllowed && values.isEmpty()))
            throw invalid(field, "Phải là mảng chuỗi hợp lệ.");
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank()) throw invalid(field, "Phải là mảng chuỗi hợp lệ.");
            result.add(value.asText().strip());
        }
        if (new HashSet<>(result).size() != result.size()) throw invalid(field, "Không được có giá trị trùng.");
        return List.copyOf(result);
    }
    public JsonNode metadata(String field) {
        JsonNode value = body.get(field);
        if (value == null) return null;
        if (!value.isObject() || value.toString().length() > 4096) throw invalid(field, "Metadata phải là object tối đa 4096 ký tự.");
        safeMetadata(value, field, 0);
        return value;
    }
    private void safeMetadata(JsonNode node, String field, int depth) {
        if (depth > 5) throw invalid(field, "Metadata quá nhiều cấp.");
        if (node.isObject()) node.fields().forEachRemaining(e -> {
            String key = e.getKey().toLowerCase(Locale.ROOT);
            if (key.contains("password") || key.contains("token") || key.contains("secret") || key.contains("credential"))
                throw invalid(field, "Metadata không được chứa thông tin xác thực.");
            safeMetadata(e.getValue(), field, depth + 1);
        });
        else if (node.isArray()) node.forEach(v -> safeMetadata(v, field, depth + 1));
    }
    public static ApiRequestException invalid(String field, String message) {
        return ApiRequestException.validation(List.of(new ApiError.FieldError(field, message)));
    }
}
