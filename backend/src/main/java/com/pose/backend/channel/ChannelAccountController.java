package com.pose.backend.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/channels/accounts")
public class ChannelAccountController {
    private final ChannelAccountService service;
    private final JwtActorContextResolver actors;

    public ChannelAccountController(ChannelAccountService service, JwtActorContextResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @GetMapping
    public ResponseEntity<List<ChannelAccountResponse>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(actors.currentActor()));
    }

    @PostMapping
    public ResponseEntity<ChannelAccountResponse> create(@RequestBody JsonNode body) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(service.create(actors.currentActor(), body));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ChannelAccountResponse> patch(@PathVariable UUID id, @RequestBody JsonNode body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.patch(actors.currentActor(), id, body));
    }
}
