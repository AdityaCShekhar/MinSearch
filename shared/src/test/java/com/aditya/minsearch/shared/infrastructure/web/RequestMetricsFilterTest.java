package com.aditya.minsearch.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestMetricsFilterTest {
  @Test void unknownPathsAndDocumentIdsCannotCreateUnboundedLabels() throws Exception {
    var registry = new SimpleMeterRegistry(); var filter = new RequestMetricsFilter(registry);
    for (int i=0;i<100;i++) {
      var request = new MockHttpServletRequest("GET", "/api/v1/documents/"+java.util.UUID.randomUUID());
      request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/documents/{id}");
      filter.doFilter(request, new MockHttpServletResponse(), (req,res)->{});
      filter.doFilter(new MockHttpServletRequest("GET", "/missing/"+i), new MockHttpServletResponse(), (req,res)->{});
    }
    assertThat(registry.getMeters()).hasSize(4);
    assertThat(registry.get("minsearch.http.request.count").tag("route", "/api/v1/documents/{id}").counter().count()).isEqualTo(100);
  }

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
