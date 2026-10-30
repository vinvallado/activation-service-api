package com.deroyque.activationserviceapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateActivationRequestPayload(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+") String externalId,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+") String customerId,
        @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9._:-]+") String productCode) { }
