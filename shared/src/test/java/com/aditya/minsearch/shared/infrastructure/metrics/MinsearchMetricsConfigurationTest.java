package com.aditya.minsearch.shared.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MinsearchMetricsConfigurationTest {

  @Test
  void registersApplicationUptimeGauge() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MinsearchMetricsConfiguration configuration = new MinsearchMetricsConfiguration();

    configuration.minsearchStartupGauge(meterRegistry);

    assertThat(meterRegistry.find("minsearch.application.up").gauge()).isNotNull();
  }
}
