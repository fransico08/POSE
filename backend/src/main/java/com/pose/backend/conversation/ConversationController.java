package com.pose.backend.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.PageResponse;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private final ConversationService service;
    private final JwtActorContextResolver actors;

    public ConversationController(ConversationService service, JwtActorContextResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ConversationResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean assignedToMe) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(actors.currentActor(), page, size, status, assignedToMe));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<PageResponse<ConversationMessageResponse>> messages(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.messages(actors.currentActor(), id, page, size));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ConversationReplyResponse> reply(@PathVariable UUID id, @RequestBody JsonNode body) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(service.reply(actors.currentActor(), id, body));
    }

    @PostMapping("/{id}/link")
    public ResponseEntity<ConversationResponse> link(@PathVariable UUID id, @RequestBody JsonNode body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.link(actors.currentActor(), id, body));
    }

    @PostMapping("/{id}/lead")
    public ResponseEntity<ConversationService.CreatedLead> createLead(@PathVariable UUID id, @RequestBody JsonNode body) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(service.createLead(actors.currentActor(),id,body));
    }

    @GetMapping("/{id}/assignees")
    public ResponseEntity<?> assignees(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.assignees(actors.currentActor(), id));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<ConversationResponse> assign(@PathVariable UUID id, @RequestBody JsonNode body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.assign(actors.currentActor(), id, body));
    }
}
