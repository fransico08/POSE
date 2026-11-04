package com.pose.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.PageResponse;
import com.pose.backend.identity.ActorContext;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService service;
    private final JwtActorContextResolver actors;

    public TaskController(TaskService service, JwtActorContextResolver actors) {
        this.service = service; this.actors = actors;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TaskResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID customerId, @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID opportunityId, @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) String status, @RequestParam(required = false) String priority,
            @RequestParam(required = false) Instant dueFrom, @RequestParam(required = false) Instant dueTo) {
        return response(200, service.list(actors.currentActor(), page, size, customerId, leadId,
                opportunityId, ownerUserId, status, priority, dueFrom, dueTo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> get(@PathVariable UUID id) {
        return response(200, service.get(actors.currentActor(), id));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@RequestBody JsonNode body) {
        return response(201, service.create(actors.currentActor(), body));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TaskResponse> patch(@PathVariable UUID id, @RequestBody JsonNode body) {
        return response(200, service.patch(actors.currentActor(), id, body));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<TaskResponse> assign(@PathVariable UUID id, @RequestBody JsonNode body) {
        return response(200, service.assign(actors.currentActor(), id, body));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<TaskResponse> start(@PathVariable UUID id, @RequestBody JsonNode body) {
        return response(200, service.transition(actors.currentActor(), id, body, "start"));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<TaskResponse> complete(@PathVariable UUID id, @RequestBody JsonNode body) {
        return response(200, service.transition(actors.currentActor(), id, body, "complete"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<TaskResponse> cancel(@PathVariable UUID id, @RequestBody JsonNode body) {
        return response(200, service.transition(actors.currentActor(), id, body, "cancel"));
    }

    private <T> ResponseEntity<T> response(int status, T value) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(value);
    }
}
