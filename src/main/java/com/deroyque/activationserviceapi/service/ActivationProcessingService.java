package com.deroyque.activationserviceapi.service;

import com.deroyque.activationserviceapi.client.ActivationProviderClient;
import com.deroyque.activationserviceapi.model.ActivationStatus;
import com.deroyque.activationserviceapi.repository.ActivationRequestRepository;
import com.deroyque.activationserviceapi.repository.ActivationTaskClaimRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ActivationProcessingService {
    private final ActivationTaskClaimRepository claimRepository; private final ActivationRequestRepository requestRepository;
    private final ActivationProviderClient provider; private final ActivationRetryPolicy retryPolicy; private final Clock clock; private final Duration lease;
    public ActivationProcessingService(ActivationTaskClaimRepository claimRepository, ActivationRequestRepository requestRepository, ActivationProviderClient provider,
                                       ActivationRetryPolicy retryPolicy, Clock clock, @Value("${activation.worker.lease:PT2M}") Duration lease) {
        this.claimRepository=claimRepository;this.requestRepository=requestRepository;this.provider=provider;this.retryPolicy=retryPolicy;this.clock=clock;this.lease=lease;
    }
    public boolean processOne() {
        var now=clock.instant(); var claim=claimRepository.claimNext(now,now.plus(lease)); if(claim.isEmpty()) return false;
        var task=claim.get(); var request=requestRepository.findById(task.requestId()).orElse(null);
        if(request == null) { claimRepository.complete(task.taskId(),task.claimToken(),ActivationStatus.FAILED,clock.instant(),"Solicitação ausente"); return true; }
        var result=provider.activate(request,task.correlationId()); var completedAt=clock.instant();
        if(result.outcome()==ActivationProviderClient.Outcome.SUCCESS) claimRepository.complete(task.taskId(),task.claimToken(),ActivationStatus.COMPLETED,completedAt,null);
        else if(result.outcome()==ActivationProviderClient.Outcome.PERMANENT_FAILURE || !retryPolicy.canRetry(task.attempt()))
            claimRepository.complete(task.taskId(),task.claimToken(),ActivationStatus.FAILED,completedAt,result.diagnostic());
        else claimRepository.reschedule(task.taskId(),task.claimToken(),retryPolicy.nextAttempt(completedAt,task.attempt(),result.retryAfterSeconds()),completedAt,result.diagnostic());
        return true;
    }
}
