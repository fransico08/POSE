package com.pose.backend.audit;

import com.pose.backend.api.PageResponse;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditQueryController {
    private final AuditQueryService service;
    private final JwtActorContextResolver actors;

    public AuditQueryController(AuditQueryService service, JwtActorContextResolver actors) {
        this.service = service; this.actors = actors;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AuditEventResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String entityType, @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String action, @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) Instant occurredFrom, @RequestParam(required = false) Instant occurredTo) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.list(actors.currentActor(), page, size,
                entityType, entityId, action, actorUserId, occurredFrom, occurredTo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditEventResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.get(actors.currentActor(), id));
    }
}
