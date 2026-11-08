package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
public class AiManagementController {
    private final AiManagementService service;private final JwtActorContextResolver actors;
    public AiManagementController(AiManagementService service,JwtActorContextResolver actors){this.service=service;this.actors=actors;}
    @GetMapping("/status") public ResponseEntity<?> status(){return ok(service.status(actors.currentActor()));}
    @GetMapping("/suggestions") public ResponseEntity<?> list(@RequestParam String subjectType,@RequestParam UUID subjectId){return ok(service.list(actors.currentActor(),subjectType,subjectId));}
    @PostMapping("/suggestions/{id}/accept") public ResponseEntity<?> accept(@PathVariable UUID id,@RequestBody(required=false) JsonNode body){return ok(service.review(actors.currentActor(),id,body,true));}
    @PostMapping("/suggestions/{id}/reject") public ResponseEntity<?> reject(@PathVariable UUID id,@RequestBody(required=false) JsonNode body){return ok(service.review(actors.currentActor(),id,body,false));}
    @GetMapping("/metrics") public ResponseEntity<?> metrics(@RequestParam Instant start,@RequestParam Instant end){return ok(service.metrics(actors.currentActor(),start,end));}
    private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().header("Cache-Control","no-store").body(body);}
}
