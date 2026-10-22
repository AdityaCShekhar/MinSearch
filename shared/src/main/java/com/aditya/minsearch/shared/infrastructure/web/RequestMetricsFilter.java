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
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
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
      String method = java.util.Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD").contains(request.getMethod()) ? request.getMethod() : "OTHER";
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
      if (route.startsWith("/api/v1/auth/")) {
        meterRegistry.counter("minsearch.auth.requests", "operation", route.substring(route.lastIndexOf('/') + 1),
            "outcome", response.getStatus() < 400 ? "success" : "rejected").increment();
      }
      if (response.getStatus() == 429) meterRegistry.counter("minsearch.security.rate.rejected").increment();
    }
  }

  private static String routeTag(HttpServletRequest request) {
    Object pattern = request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (pattern != null) return pattern.toString();
    String uri = request.getRequestURI();
    if (java.util.Set.of("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
        "/api/v1/search", "/api/v1/documents", "/api/v1/autocomplete", "/api/v1/suggest").contains(uri)) return uri;
    return "UNMATCHED";
  }
}
