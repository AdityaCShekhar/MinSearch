package com.aditya.minsearch.shared.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;

/** All labels are adapter/operation constants, never cache keys or user input. */
public final class CacheMetrics {
  private final MeterRegistry registry;
  private final String adapter;
  public CacheMetrics(MeterRegistry registry, String adapter) { this.registry=registry; this.adapter=adapter; }
  public void record(String operation, String outcome) {
    if (registry != null) registry.counter("minsearch.cache.operations", "adapter", adapter, "operation", operation, "outcome", outcome).increment();
  }
}
