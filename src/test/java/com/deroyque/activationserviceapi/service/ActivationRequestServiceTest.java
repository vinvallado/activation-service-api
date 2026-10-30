package com.deroyque.activationserviceapi.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.deroyque.activationserviceapi.dto.CreateActivationRequestPayload;
import com.deroyque.activationserviceapi.entity.ActivationRequestEntity;
import com.deroyque.activationserviceapi.exception.ActivationConflictException;
import com.deroyque.activationserviceapi.repository.ActivationRequestRepository;
import com.deroyque.activationserviceapi.repository.ActivationTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivationRequestServiceTest {
    @Mock ActivationRequestRepository requests;
    @Mock ActivationTaskRepository tasks;
    private ActivationRequestService service;
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);
    private final CreateActivationRequestPayload payload=new CreateActivationRequestPayload("EXT-1","CUST-1","PRODUCT-1");

    @BeforeEach void setUp() { service=new ActivationRequestService(requests,tasks,clock); }

    @Test void createsRequestAndDurableTask() {
        var result=service.create("client-a",payload,"corr-1");
        assertThat(result.created()).isTrue(); assertThat(result.response().externalId()).isEqualTo("EXT-1");
        verify(requests).saveAndFlush(any(ActivationRequestEntity.class)); verify(tasks).save(any());
    }

    @Test void returnsExistingResourceForSameOwnerAndPayload() {
        var existing=new ActivationRequestEntity(java.util.UUID.randomUUID(),"EXT-1","client-a","CUST-1","PRODUCT-1",clock.instant());
        when(requests.findByExternalId("EXT-1")).thenReturn(Optional.of(existing));
        var result=service.create("client-a",payload,"corr-1");
        assertThat(result.created()).isFalse(); assertThat(result.response().id()).isEqualTo(existing.getId());
        verifyNoInteractions(tasks); verify(requests,never()).saveAndFlush(any());
    }

    @Test void rejectsPayloadDifferentFromExistingExternalId() {
        var existing=new ActivationRequestEntity(java.util.UUID.randomUUID(),"EXT-1","client-a","CUST-2","PRODUCT-1",clock.instant());
        when(requests.findByExternalId("EXT-1")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.create("client-a",payload,"corr-1")).isInstanceOf(ActivationConflictException.class);
    }

    @Test void doesNotRevealAnotherOwnerResource() {
        var existing=new ActivationRequestEntity(java.util.UUID.randomUUID(),"EXT-1","client-b","CUST-1","PRODUCT-1",clock.instant());
        when(requests.findByExternalId("EXT-1")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.create("client-a",payload,"corr-1")).isInstanceOf(ActivationConflictException.class);
    }
}
