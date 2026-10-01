package com.pose.backend.organization;

import java.util.UUID;

public record OrganizationResponse(UUID id, String code, String name, String industry, boolean active) {}
