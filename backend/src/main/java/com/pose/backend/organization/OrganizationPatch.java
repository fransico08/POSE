package com.pose.backend.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.ApiError;
import com.pose.backend.api.ApiRequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record OrganizationPatch(boolean hasName, String name, boolean hasIndustry, String industry) {
    public static OrganizationPatch from(JsonNode body) {
        List<ApiError.FieldError> errors = new ArrayList<>();
        if (body == null || !body.isObject()) {
            throw ApiRequestException.validation(List.of(new ApiError.FieldError("body", "Phải là một JSON object.")));
        }
        body.fieldNames().forEachRemaining(field -> {
            if (!Set.of("name", "industry").contains(field)) {
                errors.add(new ApiError.FieldError(field, "Trường này không được phép cập nhật."));
            }
        });
        if (!body.has("name") && !body.has("industry")) {
            errors.add(new ApiError.FieldError("body", "Cần ít nhất một trường name hoặc industry."));
        }
        String name = readText(body, "name", 150, false, errors);
        String industry = readText(body, "industry", 120, true, errors);
        if (!errors.isEmpty()) throw ApiRequestException.validation(errors);
        return new OrganizationPatch(body.has("name"), name, body.has("industry"), industry);
    }

    private static String readText(JsonNode body, String field, int max, boolean nullable,
                                   List<ApiError.FieldError> errors) {
        JsonNode value = body.get(field);
        if (value == null || (nullable && value.isNull())) return null;
        if (!value.isTextual()) {
            errors.add(new ApiError.FieldError(field, "Phải là chuỗi" + (nullable ? " hoặc null." : ".")));
            return null;
        }
        String text = value.textValue().strip();
        if (text.isBlank() || text.length() > max) {
            errors.add(new ApiError.FieldError(field, "Không được trống và dài tối đa " + max + " ký tự."));
        }
        return text;
    }
}
