package com.deroyque.activationserviceapi.model;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivationRequestTest {
    private final Instant now=Instant.parse("2026-10-30T12:00:00Z");
    private ActivationRequest pending() { return ActivationRequest.pending(UUID.randomUUID(),"EXT-1","CUST-1","PRODUCT-1","client-a",now); }
    @Test void followsValidStateTransitions() {
        var request=pending(); request.start(now.plusSeconds(1)); request.complete(now.plusSeconds(2));
        assertThat(request.status()).isEqualTo(ActivationStatus.COMPLETED); assertThat(request.updatedAt()).isEqualTo(now.plusSeconds(2));
    }
    @Test void allowsTransientFailureToBeRetried() {
        var request=pending(); request.start(now); request.retry(now.plusSeconds(1));
        assertThat(request.status()).isEqualTo(ActivationStatus.PENDING);
    }
    @Test void rejectsTerminalStateReopening() {
        var request=pending(); request.start(now); request.complete(now);
        assertThatThrownBy(() -> request.start(now)).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsMissingBusinessFields() {
        assertThatThrownBy(() -> ActivationRequest.pending(UUID.randomUUID()," ","C","P","owner",now)).isInstanceOf(IllegalArgumentException.class);
    }
}
