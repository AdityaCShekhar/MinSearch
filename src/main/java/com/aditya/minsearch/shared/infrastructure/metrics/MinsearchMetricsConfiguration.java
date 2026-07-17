package com.aditya.minsearch.shared.infrastructure.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinsearchMetricsConfiguration {

  @Bean
  public AtomicInteger minsearchStartupGauge(MeterRegistry meterRegistry) {
    AtomicInteger startupStatus = new AtomicInteger(1);
    Gauge.builder("minsearch.application.up", startupStatus, AtomicInteger::get)
        .description("Application startup status gauge")
        .register(meterRegistry);
    return startupStatus;
  }
}
