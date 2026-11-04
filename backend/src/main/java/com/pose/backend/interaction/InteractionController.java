package com.pose.backend.interaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.PageResponse;
import com.pose.backend.identity.ActorContext;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interactions")
public class InteractionController {
    private final InteractionService service;
    private final JwtActorContextResolver actors;

    public InteractionController(InteractionService service, JwtActorContextResolver actors) {
        this.service = service; this.actors = actors;
    }

    @GetMapping
    public ResponseEntity<PageResponse<InteractionResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID customerId, @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID opportunityId, @RequestParam(required = false) String type,
            @RequestParam(required = false) String channel, @RequestParam(required = false) String visibility,
            @RequestParam(required = false) Instant occurredFrom, @RequestParam(required = false) Instant occurredTo) {
        return response(200, service.list(actors.currentActor(), page, size, customerId, leadId, opportunityId,
                type, channel, visibility, occurredFrom, occurredTo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InteractionResponse> get(@PathVariable UUID id) {
        return response(200, service.get(actors.currentActor(), id));
    }

    @PostMapping
    public ResponseEntity<InteractionResponse> create(@RequestBody JsonNode body) {
        return response(201, service.create(actors.currentActor(), body));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<InteractionResponse> patch(@PathVariable UUID id, @RequestBody JsonNode body) {
        return response(200, service.patch(actors.currentActor(), id, body));
    }

    private <T> ResponseEntity<T> response(int status, T value) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(value);
    }
}
