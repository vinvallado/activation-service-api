package com.deroyque.activationserviceapi.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Modelo de domínio sem dependência de Spring ou persistência. */
public final class ActivationRequest {
    private final UUID id;
    private final String externalId;
    private final String customerId;
    private final String productCode;
    private final String ownerClientId;
    private final Instant createdAt;
    private ActivationStatus status;
    private Instant updatedAt;

    public ActivationRequest(UUID id, String externalId, String customerId, String productCode, String ownerClientId,
                             ActivationStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id); this.externalId = required(externalId); this.customerId = required(customerId);
        this.productCode = required(productCode); this.ownerClientId = required(ownerClientId); this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt); this.updatedAt = Objects.requireNonNull(updatedAt);
    }
    public static ActivationRequest pending(UUID id, String externalId, String customerId, String productCode, String ownerClientId, Instant now) {
        return new ActivationRequest(id, externalId, customerId, productCode, ownerClientId, ActivationStatus.PENDING, now, now);
    }
    public void start(Instant now) { transitionTo(ActivationStatus.PROCESSING, now); }
    public void complete(Instant now) { transitionTo(ActivationStatus.COMPLETED, now); }
    public void fail(Instant now) { transitionTo(ActivationStatus.FAILED, now); }
    public void retry(Instant now) { transitionTo(ActivationStatus.PENDING, now); }
    private void transitionTo(ActivationStatus target, Instant now) {
        boolean valid = switch (status) {
            case PENDING -> target == ActivationStatus.PROCESSING;
            case PROCESSING -> target == ActivationStatus.PENDING || target == ActivationStatus.COMPLETED || target == ActivationStatus.FAILED;
            case COMPLETED, FAILED -> false;
        };
        if (!valid) throw new IllegalStateException("Transição de ativação inválida: " + status + " para " + target);
        status = target; updatedAt = Objects.requireNonNull(now);
    }
    public UUID id() { return id; } public String externalId() { return externalId; } public String customerId() { return customerId; }
    public String productCode() { return productCode; } public String ownerClientId() { return ownerClientId; }
    public ActivationStatus status() { return status; } public Instant createdAt() { return createdAt; } public Instant updatedAt() { return updatedAt; }
    private static String required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Campo obrigatório"); return value; }
}
