package com.deroyque.activationserviceapi.dto;

import java.util.List;
public record ActivationPageResponse(List<ActivationRequestResponse> content, int page, int size, long totalElements, int totalPages) { }
