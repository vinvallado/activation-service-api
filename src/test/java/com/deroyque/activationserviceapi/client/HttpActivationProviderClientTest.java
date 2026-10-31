package com.deroyque.activationserviceapi.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.deroyque.activationserviceapi.entity.ActivationRequestEntity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpActivationProviderClientTest {
    private HttpServer server;
    private volatile int status;
    private volatile String retryAfter;
    private HttpActivationProviderClient client;
    private final ActivationRequestEntity request = new ActivationRequestEntity(UUID.randomUUID(), "external", "owner", "customer", "product", Instant.now());

    @BeforeEach void startServer() throws IOException {
        status = 200;
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/provider/v1/activate", this::respond);
        server.start();
        client = new HttpActivationProviderClient(CircuitBreakerRegistry.ofDefaults(), "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(1), "", new SimpleMeterRegistry());
    }
    @AfterEach void stopServer() { server.stop(0); }

    @Test void acceptsSuccessfulProviderResponse() {
        assertThat(client.activate(request, "corr-1").outcome()).isEqualTo(ActivationProviderClient.Outcome.SUCCESS);
    }
    @Test void classifiesBadRequestAsPermanentFailure() {
        status = 400;
        var result = client.activate(request, "corr-1");
        assertThat(result.outcome()).isEqualTo(ActivationProviderClient.Outcome.PERMANENT_FAILURE);
        assertThat(result.diagnostic()).contains("recusou");
    }
    @Test void readsNumericRetryAfterForTransientFailure() {
        status = 503; retryAfter = "7";
        var result = client.activate(request, "corr-1");
        assertThat(result.outcome()).isEqualTo(ActivationProviderClient.Outcome.TRANSIENT_FAILURE);
        assertThat(result.retryAfterSeconds()).isEqualTo(7);
    }
    @Test void readsHttpDateRetryAfterForTransientFailure() {
        status = 429; retryAfter = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(5).format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
        var result = client.activate(request, "corr-1");
        assertThat(result.outcome()).isEqualTo(ActivationProviderClient.Outcome.TRANSIENT_FAILURE);
        assertThat(result.retryAfterSeconds()).isBetween(0, 5);
    }
    private void respond(HttpExchange exchange) throws IOException {
        if (retryAfter != null) exchange.getResponseHeaders().add("Retry-After", retryAfter);
        exchange.sendResponseHeaders(status, -1); exchange.close();
    }
}
