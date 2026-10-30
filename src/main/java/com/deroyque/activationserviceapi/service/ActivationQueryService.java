package com.deroyque.activationserviceapi.service;

import com.deroyque.activationserviceapi.dto.*;
import com.deroyque.activationserviceapi.exception.ActivationNotFoundException;
import com.deroyque.activationserviceapi.model.ActivationStatus;
import com.deroyque.activationserviceapi.repository.ActivationRequestRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ActivationQueryService {
    private final ActivationRequestRepository repository;
    public ActivationQueryService(ActivationRequestRepository repository) { this.repository=repository; }
    public ActivationRequestResponse findOwned(UUID id, String ownerClientId) {
        return repository.findByIdAndOwnerClientId(id, ownerClientId).map(ActivationRequestResponse::from).orElseThrow(ActivationNotFoundException::new);
    }
    public ActivationPageResponse search(String ownerClientId, ActivationStatus status, String customerId, int page, int size) {
        var pageable=PageRequest.of(page,size, Sort.by("createdAt").ascending().and(Sort.by("id").ascending()));
        var result = status != null && customerId != null ? repository.findByOwnerClientIdAndCustomerIdAndStatus(ownerClientId,customerId,status,pageable)
                : status != null ? repository.findByOwnerClientIdAndStatus(ownerClientId,status,pageable)
                : customerId != null ? repository.findByOwnerClientIdAndCustomerId(ownerClientId,customerId,pageable)
                : repository.findByOwnerClientId(ownerClientId,pageable);
        return new ActivationPageResponse(result.map(ActivationRequestResponse::from).getContent(),page,size,result.getTotalElements(),result.getTotalPages());
    }
}
