package com.pose.backend.customer;
import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.PageResponse;
import com.pose.backend.api.JsonInput;
import com.pose.backend.salesassignment.SalesAssignmentCandidateResponse;
import com.pose.backend.security.JwtActorContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService service; private final JwtActorContextResolver actors;
    public CustomerController(CustomerService service,JwtActorContextResolver actors) { this.service=service; this.actors=actors; }
    @GetMapping public ResponseEntity<PageResponse<CustomerResponse>> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String search,@RequestParam(required=false) String status,@RequestParam(required=false) String customerType,@RequestParam(required=false) UUID ownerUserId) {
        return response(200,service.list(actors.currentActor(),page,size,search,status,customerType,ownerUserId));
    }
    @GetMapping("/owner-candidates") public ResponseEntity<List<SalesAssignmentCandidateResponse>> candidates(@RequestParam(required=false) UUID offeringId) {
        if (offeringId==null) throw JsonInput.invalid("offeringId","Vui lòng điền đầy đủ các trường bắt buộc");
        return response(200,service.candidates(actors.currentActor(),offeringId));
    }
    @GetMapping("/owners") public ResponseEntity<List<SalesAssignmentCandidateResponse>> owners() { return response(200,service.owners(actors.currentActor())); }
    @GetMapping("/{id}") public ResponseEntity<CustomerResponse> get(@PathVariable UUID id) { return response(200,service.get(actors.currentActor(),id)); }
    @PostMapping public ResponseEntity<CustomerResponse> create(@RequestBody JsonNode body) { return response(201,service.create(actors.currentActor(),body)); }
    @PatchMapping("/{id}") public ResponseEntity<CustomerResponse> patch(@PathVariable UUID id,@RequestBody JsonNode body) { return response(200,service.patch(actors.currentActor(),id,body)); }
    private <T> ResponseEntity<T> response(int status,T value) { return ResponseEntity.status(status).header("Cache-Control","no-store").body(value); }
}
