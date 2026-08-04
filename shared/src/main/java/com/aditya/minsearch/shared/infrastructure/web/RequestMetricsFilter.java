package com.aditya.minsearch.shared.infrastructure.web;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class RequestMetricsFilter extends OncePerRequestFilter {

  private final MeterRegistry meterRegistry;

  public RequestMetricsFilter(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long startNanos = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationNanos = System.nanoTime() - startNanos;
      String route = routeTag(request);
      String method = request.getMethod();
      String status = Integer.toString(response.getStatus());

      Timer.builder("minsearch.http.request")
          .description("HTTP request latency")
          .tag("route", route)
          .tag("method", method)
          .tag("status", status)
          .register(meterRegistry)
          .record(durationNanos, TimeUnit.NANOSECONDS);

      Counter.builder("minsearch.http.request.count")
          .description("HTTP request count")
          .tag("route", route)
          .tag("method", method)
          .tag("status", status)
          .register(meterRegistry)
          .increment();
    }
  }

  private static String routeTag(HttpServletRequest request) {
    String servletPath = request.getServletPath();
    String pathInfo = request.getPathInfo();
    String route = (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);
    return route.isBlank() ? "UNKNOWN" : route;
  }
}
