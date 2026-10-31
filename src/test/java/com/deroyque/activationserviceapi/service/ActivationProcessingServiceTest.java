package com.deroyque.activationserviceapi.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.deroyque.activationserviceapi.client.ActivationProviderClient;
import com.deroyque.activationserviceapi.entity.ActivationRequestEntity;
import com.deroyque.activationserviceapi.model.ActivationStatus;
import com.deroyque.activationserviceapi.repository.ActivationRequestRepository;
import com.deroyque.activationserviceapi.repository.ActivationTaskClaimRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivationProcessingServiceTest {
    @Mock ActivationTaskClaimRepository claims;
    @Mock ActivationRequestRepository requests;
    @Mock ActivationProviderClient provider;
    @Mock ActivationRetryPolicy retryPolicy;
    private ActivationProcessingService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T18:00:00Z"), ZoneOffset.UTC);
    private final UUID taskId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final UUID token = UUID.randomUUID();

    @BeforeEach void setUp() {
        service = new ActivationProcessingService(claims, requests, provider, retryPolicy, clock, Duration.ofMinutes(2));
    }

    @Test void doesNothingWhenNoTaskIsAvailable() {
        when(claims.claimNext(any(), any())).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThat(service.processOne()).isFalse();
        verifyNoInteractions(requests, provider, retryPolicy);
    }

    @Test void completesRequestAfterProviderSuccess() {
        var claim = claim(1); var request = request();
        when(claims.claimNext(any(), any())).thenReturn(Optional.of(claim));
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(provider.activate(request, "corr-1")).thenReturn(new ActivationProviderClient.ProviderResult(ActivationProviderClient.Outcome.SUCCESS, null, null));
        org.assertj.core.api.Assertions.assertThat(service.processOne()).isTrue();
        verify(claims).complete(eq(taskId), eq(token), eq(ActivationStatus.COMPLETED), any(), isNull());
    }

    @Test void failsRequestAfterPermanentProviderFailure() {
        var claim = claim(1); var request = request();
        when(claims.claimNext(any(), any())).thenReturn(Optional.of(claim));
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(provider.activate(request, "corr-1")).thenReturn(new ActivationProviderClient.ProviderResult(ActivationProviderClient.Outcome.PERMANENT_FAILURE, null, "rejected"));
        service.processOne();
        verify(claims).complete(eq(taskId), eq(token), eq(ActivationStatus.FAILED), any(), eq("rejected"));
        verifyNoInteractions(retryPolicy);
    }

    @Test void reschedulesTransientProviderFailure() {
        var claim = claim(2); var request = request(); var next = clock.instant().plusSeconds(11);
        when(claims.claimNext(any(), any())).thenReturn(Optional.of(claim));
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(provider.activate(request, "corr-1")).thenReturn(new ActivationProviderClient.ProviderResult(ActivationProviderClient.Outcome.TRANSIENT_FAILURE, 3, "unavailable"));
        when(retryPolicy.canRetry(2)).thenReturn(true); when(retryPolicy.nextAttempt(any(), eq(2), eq(3))).thenReturn(next);
        service.processOne();
        verify(claims).reschedule(eq(taskId), eq(token), eq(next), any(), eq("unavailable"));
    }

    private ActivationTaskClaimRepository.ClaimedTask claim(int attempt) { return new ActivationTaskClaimRepository.ClaimedTask(taskId, requestId, token, attempt, "corr-1"); }
    private ActivationRequestEntity request() { return new ActivationRequestEntity(requestId, "ext", "owner", "customer", "product", clock.instant()); }
}
