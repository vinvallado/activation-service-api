package com.deroyque.activationserviceapi.service;

import com.deroyque.activationserviceapi.dto.ActivationRequestResponse;
import com.deroyque.activationserviceapi.dto.CreateActivationRequestPayload;
import com.deroyque.activationserviceapi.entity.ActivationRequestEntity;
import com.deroyque.activationserviceapi.entity.ActivationTaskEntity;
import com.deroyque.activationserviceapi.exception.ActivationConflictException;
import com.deroyque.activationserviceapi.repository.ActivationRequestRepository;
import com.deroyque.activationserviceapi.repository.ActivationTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivationRequestService {
    private final ActivationRequestRepository requestRepository;
    private final ActivationTaskRepository taskRepository;
    private final Clock clock;
    public ActivationRequestService(ActivationRequestRepository requestRepository, ActivationTaskRepository taskRepository, Clock clock) {
        this.requestRepository=requestRepository; this.taskRepository=taskRepository; this.clock=clock;
    }
    @Transactional
    public CreationResult create(String ownerClientId, CreateActivationRequestPayload payload, String correlationId) {
        var existing = requestRepository.findByExternalId(payload.externalId());
        if (existing.isPresent()) return sameOrConflict(existing.get(), ownerClientId, payload);
        Instant now = clock.instant();
        var request = new ActivationRequestEntity(UUID.randomUUID(), payload.externalId(), ownerClientId, payload.customerId(), payload.productCode(), now);
        try {
            requestRepository.saveAndFlush(request);
            taskRepository.save(new ActivationTaskEntity(UUID.randomUUID(), request, now, correlationId));
            return new CreationResult(ActivationRequestResponse.from(request), true);
        } catch (DataIntegrityViolationException exception) {
            throw new ActivationConflictException("A solicitação não pôde ser criada devido a uma concorrência. Repita a operação.");
        }
    }
    private CreationResult sameOrConflict(ActivationRequestEntity existing, String ownerClientId, CreateActivationRequestPayload payload) {
        if (!existing.getOwnerClientId().equals(ownerClientId)) throw new ActivationConflictException("Já existe uma solicitação com esta chave externa");
        if (!existing.getCustomerId().equals(payload.customerId()) || !existing.getProductCode().equals(payload.productCode()))
            throw new ActivationConflictException("A chave externa já foi usada com dados diferentes");
        return new CreationResult(ActivationRequestResponse.from(existing), false);
    }
    public record CreationResult(ActivationRequestResponse response, boolean created) { }
}
