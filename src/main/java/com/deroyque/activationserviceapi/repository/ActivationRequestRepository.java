package com.deroyque.activationserviceapi.repository;

import com.deroyque.activationserviceapi.entity.ActivationRequestEntity;
import com.deroyque.activationserviceapi.model.ActivationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivationRequestRepository extends JpaRepository<ActivationRequestEntity, UUID> {
    Optional<ActivationRequestEntity> findByExternalId(String externalId);
    Optional<ActivationRequestEntity> findByIdAndOwnerClientId(UUID id, String ownerClientId);
    Page<ActivationRequestEntity> findByOwnerClientId(String ownerClientId, Pageable pageable);
    Page<ActivationRequestEntity> findByOwnerClientIdAndStatus(String ownerClientId, ActivationStatus status, Pageable pageable);
    Page<ActivationRequestEntity> findByOwnerClientIdAndCustomerId(String ownerClientId, String customerId, Pageable pageable);
    Page<ActivationRequestEntity> findByOwnerClientIdAndCustomerIdAndStatus(String ownerClientId, String customerId, ActivationStatus status, Pageable pageable);
}
