package com.deroyque.activationserviceapi.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ActivationRetryPolicy {
    private final int maxAttempts;
    public ActivationRetryPolicy(@Value("${activation.worker.max-attempts:5}") int maxAttempts) { this.maxAttempts=maxAttempts; }
    public boolean canRetry(int attempt) { return attempt < maxAttempts; }
    public Instant nextAttempt(Instant now, int attempt, Integer retryAfterSeconds) {
        if(retryAfterSeconds != null) return now.plusSeconds(retryAfterSeconds);
        long seconds=Math.min(300,1L << Math.min(8,attempt)); long jitter=ThreadLocalRandom.current().nextLong(Math.max(1,seconds/2));
        return now.plus(Duration.ofSeconds(seconds+jitter));
    }
}
