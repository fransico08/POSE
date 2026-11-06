package com.pose.backend.dashboard;

import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
    private final DashboardService dashboard;
    private final JwtActorContextResolver actors;
    public DashboardController(DashboardService dashboard, JwtActorContextResolver actors) {
        this.dashboard = dashboard; this.actors = actors;
    }
    @GetMapping("/api/v1/dashboard/kpis")
    public ResponseEntity<DashboardResponse> kpis(@RequestParam MultiValueMap<String, String> parameters) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(dashboard.read(actors.currentActor(), parameters));
    }
}
