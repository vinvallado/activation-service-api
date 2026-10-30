package com.deroyque.activationserviceapi.scheduler;

import com.deroyque.activationserviceapi.service.ActivationProcessingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ActivationWorkerScheduler {
    private final ActivationProcessingService processingService;
    public ActivationWorkerScheduler(ActivationProcessingService processingService) { this.processingService=processingService; }
    @Scheduled(fixedDelayString = "${activation.worker.poll-delay-ms:1000}")
    public void processAvailableTasks() { while(processingService.processOne()) { /* drena lote elegível */ } }
}
