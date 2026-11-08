package com.pose.backend.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/copilot")
public class CopilotController {
    private final CopilotService service;
    private final JwtActorContextResolver actors;

    public CopilotController(CopilotService service, JwtActorContextResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping("/customers/{id}/summary")
    public ResponseEntity<CopilotResponse> summarizeCustomer(@PathVariable UUID id,
                                                             @RequestBody(required = false) JsonNode body) {
        return response(service.summarizeCustomer(actors.currentActor(), id, body));
    }

    @PostMapping("/customers/{id}/handover-brief")
    public ResponseEntity<CopilotResponse> generateHandoverBrief(@PathVariable UUID id,
                                                                 @RequestBody(required = false) JsonNode body) {
        return response(service.generateHandoverBrief(actors.currentActor(), id, body));
    }

    private static <T> ResponseEntity<T> response(T value) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(value);
    }
}
