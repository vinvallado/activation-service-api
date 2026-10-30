package com.deroyque.activationserviceapi.controller;

import com.deroyque.activationserviceapi.dto.*;
import com.deroyque.activationserviceapi.model.ActivationStatus;
import com.deroyque.activationserviceapi.service.ActivationQueryService;
import com.deroyque.activationserviceapi.service.ActivationRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/requests")
public class ActivationRequestController {
    private final ActivationRequestService requestService;
    private final ActivationQueryService queryService;
    public ActivationRequestController(ActivationRequestService requestService, ActivationQueryService queryService) { this.requestService=requestService; this.queryService=queryService; }
    @PostMapping
    public ResponseEntity<ActivationRequestResponse> create(@Valid @RequestBody CreateActivationRequestPayload payload, Principal principal, HttpServletRequest request) {
        var result=requestService.create(principal.getName(),payload,(String) request.getAttribute("correlationId"));
        return result.created() ? ResponseEntity.created(URI.create("/api/v1/requests/"+result.response().id())).body(result.response()) : ResponseEntity.ok(result.response());
    }
    @GetMapping("/{id}")
    public ActivationRequestResponse get(@PathVariable UUID id, Principal principal) { return queryService.findOwned(id,principal.getName()); }
    @GetMapping
    public ActivationPageResponse list(@RequestParam(required=false) ActivationStatus status, @RequestParam(required=false) String customerId,
                                       @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size, Principal principal) {
        return queryService.search(principal.getName(),status,customerId,page,size);
    }
}
