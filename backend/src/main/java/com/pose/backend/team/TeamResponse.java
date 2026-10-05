package com.pose.backend.team;

import java.util.List;
import java.util.UUID;

public record TeamResponse(UUID id, String code, String name, String description, String status,
                           UUID departmentId, UUID managerUserId, List<Member> members) {
    public record Member(UUID id, String fullName, String status) {}
}
