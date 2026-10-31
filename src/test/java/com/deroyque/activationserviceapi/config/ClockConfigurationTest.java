package com.deroyque.activationserviceapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class ClockConfigurationTest {
    @Test void exposesUtcSystemClock() {
        Clock clock = new ClockConfiguration().clock();
        assertThat(clock.getZone()).isEqualTo(java.time.ZoneOffset.UTC);
    }
}
