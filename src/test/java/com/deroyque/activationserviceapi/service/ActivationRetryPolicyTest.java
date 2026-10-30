package com.deroyque.activationserviceapi.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ActivationRetryPolicyTest {
    private final ActivationRetryPolicy policy=new ActivationRetryPolicy(3);
    @Test void limitsAttempts() { assertThat(policy.canRetry(1)).isTrue(); assertThat(policy.canRetry(2)).isTrue(); assertThat(policy.canRetry(3)).isFalse(); }
    @Test void honorsRetryAfter() { var now=Instant.parse("2026-10-30T12:00:00Z"); assertThat(policy.nextAttempt(now,1,30)).isEqualTo(now.plusSeconds(30)); }
    @Test void usesFutureTimeWithBackoff() { var now=Instant.parse("2026-10-30T12:00:00Z"); assertThat(policy.nextAttempt(now,2,null)).isAfter(now); }
}
