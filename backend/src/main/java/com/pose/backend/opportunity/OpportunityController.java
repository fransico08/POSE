package com.pose.backend.opportunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.PageResponse;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/opportunities")
public class OpportunityController {
    private final OpportunityService service;
    private final JwtActorContextResolver actors;

    public OpportunityController(OpportunityService service, JwtActorContextResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @GetMapping
    public ResponseEntity<PageResponse<OpportunityResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) UUID ownerUserId) {
        return response(200, service.list(actors.currentActor(), page, size, search, stage, ownerUserId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OpportunityResponse> get(@PathVariable UUID id) {
        return response(200, service.get(actors.currentActor(), id));
    }

    @PostMapping
    public ResponseEntity<OpportunityResponse> create(@RequestBody JsonNode body) {
        return response(201, service.create(actors.currentActor(), body));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<OpportunityResponse> update(@PathVariable UUID id, @RequestBody JsonNode body) {
        return response(200, service.update(actors.currentActor(), id, body));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<OpportunityResponse> reopen(@PathVariable UUID id, @RequestBody JsonNode body) {
        return response(200, service.reopen(actors.currentActor(), id, body));
    }

    private static <T> ResponseEntity<T> response(int status, T value) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(value);
    }
}
