package com.deroyque.activationserviceapi.entity;

import com.deroyque.activationserviceapi.model.ActivationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activation_requests", schema = "activation_service")
public class ActivationRequestEntity {
    @Id private UUID id;
    @Column(name = "external_id", nullable = false, unique = true, length = 100) private String externalId;
    @Column(name = "owner_client_id", nullable = false, length = 100) private String ownerClientId;
    @Column(name = "customer_id", nullable = false, length = 100) private String customerId;
    @Column(name = "product_code", nullable = false, length = 80) private String productCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ActivationStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected ActivationRequestEntity() { }
    public ActivationRequestEntity(UUID id, String externalId, String ownerClientId, String customerId, String productCode, Instant now) {
        this.id=id; this.externalId=externalId; this.ownerClientId=ownerClientId; this.customerId=customerId; this.productCode=productCode;
        this.status=ActivationStatus.PENDING; this.createdAt=now; this.updatedAt=now;
    }
    public UUID getId(){return id;} public String getExternalId(){return externalId;} public String getOwnerClientId(){return ownerClientId;}
    public String getCustomerId(){return customerId;} public String getProductCode(){return productCode;} public ActivationStatus getStatus(){return status;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public void setStatus(ActivationStatus status, Instant now){this.status=status;this.updatedAt=now;}
}
