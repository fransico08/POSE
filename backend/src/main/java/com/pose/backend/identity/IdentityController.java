package com.pose.backend.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.PageResponse;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    private final UserService users;
    private final RoleService roles;
    private final JwtActorContextResolver actors;
    public IdentityController(UserService users,RoleService roles,JwtActorContextResolver actors) { this.users=users; this.roles=roles; this.actors=actors; }
    @GetMapping("/users") public ResponseEntity<PageResponse<UserResponse>> users(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String search) {
        return response(200,users.list(actors.currentActor(),page,size,search));
    }
    @GetMapping("/users/{id}") public ResponseEntity<UserResponse> user(@PathVariable UUID id) { return response(200,users.get(actors.currentActor(),id)); }
    @PostMapping("/users") public ResponseEntity<UserResponse> create(@RequestBody JsonNode body) { return response(201,users.create(actors.currentActor(),body)); }
    @PatchMapping("/users/{id}") public ResponseEntity<UserResponse> patch(@PathVariable UUID id,@RequestBody JsonNode body) { return response(200,users.patch(actors.currentActor(),id,body)); }
    @PutMapping("/users/{id}/roles") public ResponseEntity<UserResponse> setRoles(@PathVariable UUID id,@RequestBody JsonNode body) { return response(200,users.roles(actors.currentActor(),id,body)); }
    @GetMapping("/roles") public ResponseEntity<List<RoleService.RoleSummary>> roles() { return response(200,roles.roles(actors.currentActor())); }
    @GetMapping("/permissions") public ResponseEntity<List<RoleService.PermissionSummary>> permissions() { return response(200,roles.permissions(actors.currentActor())); }
    @GetMapping("/roles/{code}/permissions") public ResponseEntity<List<String>> permissions(@PathVariable String code) { return response(200,roles.rolePermissions(actors.currentActor(),code)); }
    @PutMapping("/roles/{code}/permissions") public ResponseEntity<List<String>> permissions(@PathVariable String code,@RequestBody JsonNode body) { return response(200,roles.setPermissions(actors.currentActor(),code,body)); }
    private <T> ResponseEntity<T> response(int status,T value) { return ResponseEntity.status(status).header("Cache-Control","no-store").body(value); }
}
