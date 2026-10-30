package com.deroyque.activationserviceapi.client;

import com.deroyque.activationserviceapi.entity.ActivationRequestEntity;

public interface ActivationProviderClient {
    ProviderResult activate(ActivationRequestEntity request, String correlationId);
    record ProviderResult(Outcome outcome, Integer retryAfterSeconds, String diagnostic) { }
    enum Outcome { SUCCESS, PERMANENT_FAILURE, TRANSIENT_FAILURE }
}
