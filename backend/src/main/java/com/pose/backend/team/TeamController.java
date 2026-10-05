package com.pose.backend.team;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.PageResponse;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {
    private final TeamService teams;
    private final JwtActorContextResolver actors;
    public TeamController(TeamService teams,JwtActorContextResolver actors) { this.teams=teams; this.actors=actors; }
    @GetMapping public ResponseEntity<PageResponse<TeamResponse>> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return response(200,teams.list(actors.currentActor(),page,size));
    }
    @GetMapping("/{id}") public ResponseEntity<TeamResponse> get(@PathVariable UUID id) { return response(200,teams.get(actors.currentActor(),id)); }
    @PostMapping public ResponseEntity<TeamResponse> create(@RequestBody JsonNode body) { return response(201,teams.create(actors.currentActor(),body)); }
    @PatchMapping("/{id}") public ResponseEntity<TeamResponse> patch(@PathVariable UUID id,@RequestBody JsonNode body) { return response(200,teams.patch(actors.currentActor(),id,body)); }
    @PutMapping("/{id}/members") public ResponseEntity<List<TeamResponse.Member>> members(@PathVariable UUID id,@RequestBody JsonNode body) {
        return response(200,teams.members(actors.currentActor(),id,body));
    }
    private <T> ResponseEntity<T> response(int status,T value) { return ResponseEntity.status(status).header("Cache-Control","no-store").body(value); }
}
