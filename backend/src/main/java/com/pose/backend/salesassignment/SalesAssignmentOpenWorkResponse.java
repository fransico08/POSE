package com.pose.backend.salesassignment;

import java.util.List;
import java.util.UUID;

public record SalesAssignmentOpenWorkResponse(long totalItems, List<Item> items) {
    public SalesAssignmentOpenWorkResponse {
        items = List.copyOf(items);
    }

    public record Item(String type, UUID id, String title, String status) {
    }
}
