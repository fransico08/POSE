package com.pose.backend.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(UUID id, UUID actorUserId, String actorType, String action,
                                 String entityType, UUID entityId, Map<String, Object> beforeData,
                                 Map<String, Object> afterData, Map<String, Object> safeMetadata,
                                 String result, Instant occurredAt) {}
