package com.pose.backend.opportunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.*;
import com.pose.backend.salesassignment.SalesAssignmentCandidateResponse;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/opportunities")
public class OpportunityController {
    private final OpportunityService service; private final JwtActorContextResolver actors;
    public OpportunityController(OpportunityService service,JwtActorContextResolver actors) { this.service=service;this.actors=actors; }
    @GetMapping public ResponseEntity<PageResponse<OpportunityResponse>> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String search,@RequestParam(required=false) String stage,@RequestParam(required=false) UUID customerId,
            @RequestParam(required=false) UUID leadId,@RequestParam(required=false) UUID ownerUserId,@RequestParam(required=false) LocalDate expectedCloseFrom,@RequestParam(required=false) LocalDate expectedCloseTo) {
        return response(200,service.list(actors.currentActor(),page,size,search,stage,customerId,leadId,ownerUserId,expectedCloseFrom,expectedCloseTo));
    }
    @GetMapping("/owners") public ResponseEntity<List<SalesAssignmentCandidateResponse>> owners() { return response(200,service.owners(actors.currentActor())); }
    @GetMapping("/owner-candidates") public ResponseEntity<List<SalesAssignmentCandidateResponse>> candidates(@RequestParam(required=false) UUID customerId,@RequestParam(required=false) String offeringIds) {
        if(offeringIds==null||offeringIds.isBlank()) throw JsonInput.invalid("offeringIds","Vui lòng chọn sản phẩm/dịch vụ.");
        List<UUID> ids=new ArrayList<>();try { for(String value:offeringIds.split(",",-1)) { UUID id=UUID.fromString(value); if(!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException(); ids.add(id); } } catch(IllegalArgumentException ex) { throw JsonInput.invalid("offeringIds","UUID không hợp lệ."); }
        return response(200,service.candidates(actors.currentActor(),customerId,ids));
    }
    @GetMapping("/{id}") public ResponseEntity<OpportunityResponse> get(@PathVariable UUID id) { return response(200,service.get(actors.currentActor(),id)); }
    @PostMapping public ResponseEntity<OpportunityResponse> create(@RequestBody JsonNode body) { return response(201,service.create(actors.currentActor(),body)); }
    @PatchMapping("/{id}") public ResponseEntity<OpportunityResponse> patch(@PathVariable UUID id,@RequestBody JsonNode body) { return response(200,service.patch(actors.currentActor(),id,body)); }
    @PostMapping("/{id}/win") public ResponseEntity<OpportunityResponse> win(@PathVariable UUID id,@RequestBody JsonNode body) { return response(200,service.transition(actors.currentActor(),id,body,"win")); }
    @PostMapping("/{id}/lose") public ResponseEntity<OpportunityResponse> lose(@PathVariable UUID id,@RequestBody JsonNode body) { return response(200,service.transition(actors.currentActor(),id,body,"lose")); }
    @PostMapping("/{id}/cancel") public ResponseEntity<OpportunityResponse> cancel(@PathVariable UUID id,@RequestBody JsonNode body) { return response(200,service.transition(actors.currentActor(),id,body,"cancel")); }
    @PostMapping("/{id}/reopen") public ResponseEntity<OpportunityResponse> reopen(@PathVariable UUID id,@RequestBody JsonNode body) { return response(200,service.transition(actors.currentActor(),id,body,"reopen")); }
    private <T> ResponseEntity<T> response(int status,T value) { return ResponseEntity.status(status).header("Cache-Control","no-store").body(value); }
}
