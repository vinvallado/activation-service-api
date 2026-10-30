package com.deroyque.activationserviceapi.repository;

import com.deroyque.activationserviceapi.model.ActivationStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ActivationTaskClaimRepository {
    Optional<ClaimedTask> claimNext(Instant now, Instant leaseUntil);
    boolean complete(UUID taskId, UUID claimToken, ActivationStatus requestStatus, Instant now, String error);
    boolean reschedule(UUID taskId, UUID claimToken, Instant nextAttemptAt, Instant now, String error);
    record ClaimedTask(UUID taskId, UUID requestId, UUID claimToken, int attempt, String correlationId) { }
}
