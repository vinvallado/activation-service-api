package com.deroyque.activationserviceapi.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.deroyque.activationserviceapi.entity.ActivationRequestEntity;
import com.deroyque.activationserviceapi.exception.ActivationNotFoundException;
import com.deroyque.activationserviceapi.model.ActivationStatus;
import com.deroyque.activationserviceapi.repository.ActivationRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class ActivationQueryServiceTest {
    @Mock ActivationRequestRepository repository;
    private ActivationQueryService service;
    private final ActivationRequestEntity request = new ActivationRequestEntity(UUID.randomUUID(), "external", "client", "customer", "product", Instant.parse("2026-10-30T18:00:00Z"));
    @BeforeEach void setUp() { service = new ActivationQueryService(repository); }

    @Test void returnsOnlyOwnedRequest() {
        when(repository.findByIdAndOwnerClientId(request.getId(), "client")).thenReturn(Optional.of(request));
        assertThat(service.findOwned(request.getId(), "client").externalId()).isEqualTo("external");
    }
    @Test void hidesMissingOrForeignRequest() {
        when(repository.findByIdAndOwnerClientId(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findOwned(UUID.randomUUID(), "client")).isInstanceOf(ActivationNotFoundException.class);
    }
    @Test void searchesEverySupportedFilterCombination() {
        var page = new PageImpl<>(List.of(request));
        when(repository.findByOwnerClientId(anyString(), any())).thenReturn(page);
        when(repository.findByOwnerClientIdAndStatus(anyString(), any(), any())).thenReturn(page);
        when(repository.findByOwnerClientIdAndCustomerId(anyString(), anyString(), any())).thenReturn(page);
        when(repository.findByOwnerClientIdAndCustomerIdAndStatus(anyString(), anyString(), any(), any())).thenReturn(page);
        assertThat(service.search("client", null, null, 0, 20).totalElements()).isOne();
        assertThat(service.search("client", ActivationStatus.PENDING, null, 0, 20).content()).hasSize(1);
        assertThat(service.search("client", null, "customer", 0, 20).content()).hasSize(1);
        assertThat(service.search("client", ActivationStatus.PENDING, "customer", 0, 20).content()).hasSize(1);
    }
}
