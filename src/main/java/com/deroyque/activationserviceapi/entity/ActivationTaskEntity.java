package com.deroyque.activationserviceapi.entity;

import com.deroyque.activationserviceapi.model.ActivationTaskStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activation_tasks", schema = "activation_service")
public class ActivationTaskEntity {
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "request_id", nullable = false, unique = true) private ActivationRequestEntity request;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ActivationTaskStatus status;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "lease_until") private Instant leaseUntil;
    @Column(name = "claim_token") private UUID claimToken;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "correlation_id", length = 100) private String correlationId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected ActivationTaskEntity() { }
    public ActivationTaskEntity(UUID id, ActivationRequestEntity request, Instant now, String correlationId) {
        this.id=id; this.request=request; this.status=ActivationTaskStatus.PENDING; this.nextAttemptAt=now;
        this.createdAt=now; this.updatedAt=now; this.correlationId=correlationId;
    }
}
