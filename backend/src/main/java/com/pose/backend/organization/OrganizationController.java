package com.pose.backend.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organization")
public class OrganizationController {
    private final OrganizationService organizations;
    private final JwtActorContextResolver actors;

    public OrganizationController(OrganizationService organizations, JwtActorContextResolver actors) {
        this.organizations = organizations;
        this.actors = actors;
    }

    @GetMapping
    public ResponseEntity<OrganizationResponse> current() {
        return response(organizations.current(actors.currentActor()));
    }

    @PatchMapping
    public ResponseEntity<OrganizationResponse> patch(@RequestBody JsonNode body) {
        return response(organizations.patch(actors.currentActor(), OrganizationPatch.from(body)));
    }

    private ResponseEntity<OrganizationResponse> response(OrganizationResponse value) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(value);
    }
}
