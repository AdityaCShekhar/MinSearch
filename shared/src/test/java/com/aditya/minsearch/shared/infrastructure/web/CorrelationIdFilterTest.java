package com.aditya.minsearch.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  @Test
  void propagatesClientCorrelationIdAndClearsMdc() throws Exception {
    CorrelationIdFilter filter = new CorrelationIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
    request.addHeader(CorrelationIdFilter.HEADER_NAME, "01J2R9X9FCG8YQXK5Y5AXYPC7M");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (servletRequest, servletResponse) -> {
          assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("01J2R9X9FCG8YQXK5Y5AXYPC7M");
        });

    assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME))
        .isEqualTo("01J2R9X9FCG8YQXK5Y5AXYPC7M");
    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }
}
