package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.JsonInput;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/signals")
public class CustomerSignalController {
    private final CustomerSignalService service; private final JwtActorContextResolver actors;
    public CustomerSignalController(CustomerSignalService service,JwtActorContextResolver actors){this.service=service;this.actors=actors;}
    @GetMapping public ResponseEntity<?> list(@RequestParam String subjectType,@RequestParam UUID subjectId){return ok(service.list(actors.currentActor(),subjectType,subjectId));}
    @PostMapping("/extract") public ResponseEntity<?> extract(@RequestBody JsonNode body){
        JsonInput.of(body,"subjectType","subjectId");
        String type=body.path("subjectType").asText();
        UUID id;try{id=UUID.fromString(body.path("subjectId").asText());}catch(IllegalArgumentException ex){throw JsonInput.invalid("subjectId","Mã hồ sơ không hợp lệ.");}
        return ok(service.extract(actors.currentActor(),type,id));
    }
    @PostMapping("/{id}/{action:accept|edit|reject}") public ResponseEntity<?> review(@PathVariable UUID id,@PathVariable String action,@RequestBody(required=false) JsonNode body){return ok(service.review(actors.currentActor(),id,action,body));}
    private static ResponseEntity<?> ok(Object body){return ResponseEntity.ok().header("Cache-Control","no-store").body(body);}
}
