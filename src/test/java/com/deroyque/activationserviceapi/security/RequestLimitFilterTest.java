package com.deroyque.activationserviceapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLimitFilterTest {
    @Test void rejectsBodyLargerThanConfiguredLimit() throws Exception {
        var filter = new RequestLimitFilter(3, 10); var request = new MockHttpServletRequest(); request.setContent(new byte[4]);
        var response = new MockHttpServletResponse(); filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(413);
    }
    @Test void rejectsRequestsAboveConfiguredRate() throws Exception {
        var filter = new RequestLimitFilter(100, 1); var request = new MockHttpServletRequest(); request.setRemoteAddr("test-ip");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        var response = new MockHttpServletResponse(); filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(429); assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    }
}
