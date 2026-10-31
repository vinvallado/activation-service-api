package com.deroyque.activationserviceapi.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
    @Test void preservesProvidedCorrelationId() throws Exception {
        var request = new MockHttpServletRequest(); request.addHeader(CorrelationIdFilter.HEADER, "corr-1"); var response = new MockHttpServletResponse();
        new CorrelationIdFilter().doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("corr-1"); assertThat(request.getAttribute("correlationId")).isEqualTo("corr-1");
    }
    @Test void generatesCorrelationIdWhenHeaderIsAbsent() throws Exception {
        var request = new MockHttpServletRequest(); var response = new MockHttpServletResponse();
        new CorrelationIdFilter().doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
    }
}
