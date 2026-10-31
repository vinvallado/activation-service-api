package com.deroyque.activationserviceapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.deroyque.activationserviceapi.dto.ActivationPageResponse;
import com.deroyque.activationserviceapi.dto.ActivationRequestResponse;
import com.deroyque.activationserviceapi.dto.CreateActivationRequestPayload;
import com.deroyque.activationserviceapi.model.ActivationStatus;
import com.deroyque.activationserviceapi.service.ActivationQueryService;
import com.deroyque.activationserviceapi.service.ActivationRequestService;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ActivationRequestControllerTest {
    @Test void createsOrReturnsIdempotentResourceAndDelegatesQueries() {
        var commands = mock(ActivationRequestService.class); var queries = mock(ActivationQueryService.class);
        var controller = new ActivationRequestController(commands, queries); var id = UUID.randomUUID();
        var response = new ActivationRequestResponse(id, "ext", "customer", "product", ActivationStatus.PENDING, Instant.now(), Instant.now());
        var payload = new CreateActivationRequestPayload("ext", "customer", "product"); var principal = (Principal) () -> "client";
        var request = new MockHttpServletRequest(); request.setAttribute("correlationId", "corr");
        when(commands.create(eq("client"), eq(payload), eq("corr"))).thenReturn(new ActivationRequestService.CreationResult(response, true));
        assertThat(controller.create(payload, principal, request).getStatusCode().value()).isEqualTo(201);
        when(commands.create(eq("client"), eq(payload), eq("corr"))).thenReturn(new ActivationRequestService.CreationResult(response, false));
        assertThat(controller.create(payload, principal, request).getStatusCode().value()).isEqualTo(200);
        when(queries.findOwned(id, "client")).thenReturn(response); assertThat(controller.get(id, principal)).isEqualTo(response);
        var page = new ActivationPageResponse(List.of(response), 0, 20, 1, 1); when(queries.search("client", ActivationStatus.PENDING, "customer", 0, 20)).thenReturn(page);
        assertThat(controller.list(ActivationStatus.PENDING, "customer", 0, 20, principal)).isEqualTo(page);
    }
}
