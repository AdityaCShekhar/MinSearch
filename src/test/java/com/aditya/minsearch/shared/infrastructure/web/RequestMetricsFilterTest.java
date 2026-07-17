package com.aditya.minsearch.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestMetricsFilterTest {

  @Test
  void recordsRequestCountAndLatency() throws Exception {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    RequestMetricsFilter filter = new RequestMetricsFilter(meterRegistry);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health/liveness");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (servletRequest, servletResponse) -> {
          ((MockHttpServletResponse) servletResponse).setStatus(200);
        });

    assertThat(meterRegistry.find("minsearch.http.request.count").counter()).isNotNull();
    assertThat(meterRegistry.find("minsearch.http.request").timer()).isNotNull();
  }
}
