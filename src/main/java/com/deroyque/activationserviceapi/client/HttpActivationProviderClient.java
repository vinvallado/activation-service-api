package com.deroyque.activationserviceapi.client;

import com.deroyque.activationserviceapi.entity.ActivationRequestEntity;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpActivationProviderClient implements ActivationProviderClient {
    private final HttpClient client;
    private final CircuitBreaker circuitBreaker;
    private final URI endpoint;
    private final Duration requestTimeout;
    private final MeterRegistry meterRegistry;
    private final String scenario;
    public HttpActivationProviderClient(CircuitBreakerRegistry registry, @Value("${activation.provider.base-url:http://127.0.0.1:9099}") String baseUrl,
                                        @Value("${activation.provider.timeout:PT3S}") Duration requestTimeout,
                                        @Value("${activation.provider.scenario:}") String scenario, MeterRegistry meterRegistry) {
        this.client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(); this.circuitBreaker=registry.circuitBreaker("activationProvider");
        this.endpoint=URI.create(baseUrl+"/provider/v1/activate"); this.requestTimeout=requestTimeout; this.scenario=scenario; this.meterRegistry=meterRegistry;
    }
    @Override public ProviderResult activate(ActivationRequestEntity request, String correlationId) {
        Supplier<ProviderResult> decorated=CircuitBreaker.decorateSupplier(circuitBreaker, () -> invoke(request, correlationId));
        var sample=Timer.start(meterRegistry);
        try {
            ProviderResult result=decorated.get();
            meterRegistry.counter("activation.provider.calls", "outcome", result.outcome().name()).increment();
            return result;
        } catch (Exception exception) {
            meterRegistry.counter("activation.provider.calls", "outcome", Outcome.TRANSIENT_FAILURE.name()).increment();
            return new ProviderResult(Outcome.TRANSIENT_FAILURE,null,"Falha temporária na integração");
        } finally { sample.stop(meterRegistry.timer("activation.provider.duration")); }
    }
    private ProviderResult invoke(ActivationRequestEntity request, String correlationId) {
        try {
            String body="{\"requestId\":\""+request.getId()+"\",\"customerId\":\""+request.getCustomerId()+"\",\"productCode\":\""+request.getProductCode()+"\"}";
            var builder=HttpRequest.newBuilder(endpoint).timeout(requestTimeout).header("Content-Type","application/json")
                    .header("Idempotency-Key",request.getId().toString()).header("X-Correlation-Id",correlationId == null ? "" : correlationId)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (!scenario.isBlank()) builder.header("X-Provider-Scenario",scenario);
            var httpRequest=builder.build();
            var response=client.send(httpRequest,HttpResponse.BodyHandlers.discarding()); int code=response.statusCode();
            if(code >= 200 && code < 300) return new ProviderResult(Outcome.SUCCESS,null,null);
            if(code == 400) return new ProviderResult(Outcome.PERMANENT_FAILURE,null,"O provedor recusou a ativação");
            return new ProviderResult(Outcome.TRANSIENT_FAILURE,retryAfter(response),"O provedor está indisponível");
        } catch (Exception exception) { return new ProviderResult(Outcome.TRANSIENT_FAILURE,null,"Tempo limite ou erro de comunicação com o provedor"); }
    }
    private Integer retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").map(value -> {
            try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {
                try { return Math.max(0,(int)Duration.between(java.time.Instant.now(),ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds()); } catch (Exception invalid) { return null; }
            }
        }).orElse(null);
    }
}
