package com.pose.backend.api;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, long totalPages) {
    public PageResponse(List<T> items, int page, int size, long totalItems) {
        this(List.copyOf(items), page, size, totalItems, (totalItems + size - 1) / size);
    }
    public static void validate(int page, int size) {
        if (page < 0 || page > 1000000) throw JsonInput.invalid("page", "Page phải từ 0 đến 1000000.");
        if (size < 1 || size > 100) throw JsonInput.invalid("size", "Size phải từ 1 đến 100.");
    }
}
