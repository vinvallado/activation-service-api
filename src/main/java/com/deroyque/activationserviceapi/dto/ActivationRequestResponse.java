package com.deroyque.activationserviceapi.dto;

import com.deroyque.activationserviceapi.entity.ActivationRequestEntity;
import com.deroyque.activationserviceapi.model.ActivationStatus;
import java.time.Instant;
import java.util.UUID;

public record ActivationRequestResponse(UUID id, String externalId, String customerId, String productCode,
                                        ActivationStatus status, Instant createdAt, Instant updatedAt) {
    public static ActivationRequestResponse from(ActivationRequestEntity entity) {
        return new ActivationRequestResponse(entity.getId(), entity.getExternalId(), entity.getCustomerId(),
                entity.getProductCode(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
