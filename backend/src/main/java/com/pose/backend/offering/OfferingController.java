package com.pose.backend.offering;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.PageResponse;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/offerings")
public class OfferingController {
    private final OfferingService offerings;
    private final JwtActorContextResolver actors;

    public OfferingController(OfferingService offerings, JwtActorContextResolver actors) {
        this.offerings = offerings;
        this.actors = actors;
    }

    @GetMapping
    public ResponseEntity<PageResponse<OfferingResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) UUID teamId) {
        return response(200, offerings.list(actors.currentActor(), page, size, search, kind, categoryId, active, teamId));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<OfferingCategoryResponse>> categories() {
        return response(200, offerings.categories(actors.currentActor()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OfferingResponse> get(@PathVariable UUID id) {
        return response(200, offerings.get(actors.currentActor(), id));
    }

    @PostMapping
    public ResponseEntity<OfferingResponse> create(@RequestBody JsonNode body) {
        return response(201, offerings.create(actors.currentActor(), body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OfferingResponse> update(@PathVariable UUID id, @RequestBody JsonNode body) {
        return response(200, offerings.update(actors.currentActor(), id, body));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<OfferingResponse> activate(@PathVariable UUID id) {
        return response(200, offerings.activate(actors.currentActor(), id));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<OfferingResponse> deactivate(@PathVariable UUID id) {
        return response(200, offerings.deactivate(actors.currentActor(), id));
    }

    private <T> ResponseEntity<T> response(int status, T value) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(value);
    }
}
