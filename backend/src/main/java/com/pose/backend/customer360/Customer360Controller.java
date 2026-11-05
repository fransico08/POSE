package com.pose.backend.customer360;

import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class Customer360Controller {
    private final Customer360Service service;
    private final JwtActorContextResolver actors;
    public Customer360Controller(Customer360Service service, JwtActorContextResolver actors) {
        this.service=service; this.actors=actors;
    }
    @GetMapping("/api/v1/customers/{id}/360")
    public ResponseEntity<Customer360Response> profile(@PathVariable UUID id) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.profile(actors.currentActor(),id));
    }
}
