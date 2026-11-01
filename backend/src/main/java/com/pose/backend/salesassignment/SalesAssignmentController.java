package com.pose.backend.salesassignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.PageResponse;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales-assignments")
public class SalesAssignmentController {
    private final SalesAssignmentService assignments;
    private final JwtActorContextResolver actors;

    public SalesAssignmentController(SalesAssignmentService assignments, JwtActorContextResolver actors) {
        this.assignments = assignments;
        this.actors = actors;
    }

    @GetMapping
    public ResponseEntity<PageResponse<SalesAssignmentResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID offeringId,
            @RequestParam(required = false) String salesSearch,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String validity) {
        return response(200, assignments.list(actors.currentActor(), page, size, search,
                teamId, offeringId, salesSearch, status, validity));
    }

    @GetMapping("/candidates")
    public ResponseEntity<List<SalesAssignmentCandidateResponse>> candidates(@RequestParam UUID offeringId) {
        return response(200, assignments.candidates(actors.currentActor(), offeringId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalesAssignmentResponse> get(@PathVariable UUID id) {
        return response(200, assignments.get(actors.currentActor(), id));
    }

    @GetMapping("/{id}/open-work")
    public ResponseEntity<SalesAssignmentOpenWorkResponse> openWork(@PathVariable UUID id) {
        return response(200, assignments.openWork(actors.currentActor(), id));
    }

    @PostMapping
    public ResponseEntity<SalesAssignmentResponse> create(@RequestBody JsonNode body) {
        return response(201, assignments.create(actors.currentActor(), body));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SalesAssignmentResponse> extend(@PathVariable UUID id, @RequestBody JsonNode body) {
        return response(200, assignments.extend(actors.currentActor(), id, body));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<SalesAssignmentResponse> revoke(@PathVariable UUID id) {
        return response(200, assignments.revoke(actors.currentActor(), id));
    }

    private <T> ResponseEntity<T> response(int status, T value) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(value);
    }
}
